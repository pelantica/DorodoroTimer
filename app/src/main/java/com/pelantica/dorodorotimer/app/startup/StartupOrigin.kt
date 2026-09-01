package com.pelantica.dorodorotimer.app.startup

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 「このプロセスは**誰に**起こされたか」「**前回どう死んだか**」を OS に問い合わせる小さな窓口。
 * [ANR-05] の分岐条件（背面起動だけ重くする＋安全弁）に使う。
 *
 * - [isBackgroundStart] … `ActivityManager.getHistoricalProcessStartReasons`（API 35+）
 * - [lastExitWasAnr]    … `ActivityManager.getHistoricalProcessExitReasons`（API 30+）
 *
 * 背面起動だけを狙う理由: 背面起動には入力が無く、`onCreate` を見張る締切は AMS の
 * `BIND_APPLICATION_TIMEOUT`（**15秒** × `ro.hw_timeout_multiplier`）ひとつだけ。
 * 破ると `Process ... failed to complete startup` を Reason に**ダイアログなしで無言 kill** され、
 * 痕跡は ApplicationExitInfo（reason=6 (ANR) / subreason=34）と、次回起動時に
 * Crashlytics が回収するレポートだけになる（エミュ API 37 実測: Start proc から
 * 15.3〜15.4秒で打ち切り。再現しないときはまず multiplier を確認）。
 */
internal object StartupOrigin {

    private const val TAG = "StartupOrigin"

    /**
     * このプロセスが**背面の仕掛け**（ジョブ / ブロードキャスト / アラーム）に起こされたか。
     *
     * API 35+ は `getHistoricalProcessStartReasons(1)` の最新1件の reason で判定する
     * （レコードはプロセス生成時に書かれるので `onCreate` から読んでも既に載っている）。
     * pid 照合はしない: **COLD 起動のレコードは pid=0 のまま**なので（実測）、照合すると
     * 狙いの「冷えたプロセスがジョブに起こされる」ケースだけが必ず false になる。
     * ALARM を含めるのは、タイマー終了アラームの起動が BROADCAST ではなく
     * **START_REASON_ALARM** と記録されるため（実測。忘れると最も自然な経路を取り逃す）。
     *
     * API 35 未満・レコード空・例外時は importance にフォールバックし、それも失敗したら
     * **false**（前面扱い）。誤って重くして文鎮化するより、軽いまま起動する方が安全側だから。
     */
    fun isBackgroundStart(context: Context): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching { activityManager.getHistoricalProcessStartReasons(1) }
                .onFailure { Log.w(TAG, "getHistoricalProcessStartReasons failed", it) }
                .getOrNull()
                ?.firstOrNull()
                ?.let { startInfo ->
                    val background = isBackgroundStartReason(startInfo.reason)
                    Log.d(TAG, "start reason=${startInfo.reason} background=$background")
                    return background
                }
        }
        return runCatching { fallbackIsBackgroundByImportance() }
            .onFailure { Log.w(TAG, "getMyMemoryState fallback failed", it) }
            .getOrDefault(false)
    }

    /**
     * 前回このパッケージのプロセスが **ANR で死んだ**か（最新の [ApplicationExitInfo] 1件。
     * Crashlytics が起動時に前回の死因を拾うのと同じ読み方）。API 30 未満は false。
     *
     * 用途は**安全弁**（`isBackgroundStart && !lastExitWasAnr` の第2項）:
     * ①背面 ANR 死 → ジョブ再スケジュール → また ANR、の無限ループを断つ（鳴るのは1武装1発）
     * ②初期化が前面起動まで殺すようになっても、ANR 死の次の起動は必ず軽い（文鎮化防止）。
     */
    fun lastExitWasAnr(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return false
        return runCatching {
            activityManager
                .getHistoricalProcessExitReasons(context.packageName, /* pid = */ 0, /* maxNum = */ 1)
                .firstOrNull()
                ?.reason == ApplicationExitInfo.REASON_ANR
        }
            .onFailure { Log.w(TAG, "getHistoricalProcessExitReasons failed", it) }
            .onSuccess { Log.d(TAG, "lastExitWasAnr=$it") }
            .getOrDefault(false)
    }

    /**
     * [ApplicationStartInfo.getReason] が「背面の仕掛けに起こされた」を意味するか。
     *
     * Android API に触らない純粋関数なので、ここだけユニットテストで固定できる
     * （[isBackgroundStart] 本体は `ActivityManager` 直叩きなのでテスト対象にしない）。
     */
    internal fun isBackgroundStartReason(reason: Int): Boolean = when (reason) {
        ApplicationStartInfo.START_REASON_JOB,
        ApplicationStartInfo.START_REASON_BROADCAST,
        ApplicationStartInfo.START_REASON_ALARM,
        -> true

        else -> false
    }

    /**
     * API 35 未満などで [ApplicationStartInfo] が使えないときの代用。
     * 「起動のきっかけ」ではなく「今の重要度」を見るので厳密には別物だが、
     * `onCreate` の時点でまだ Activity が無いプロセスは
     * [ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE] 以下の扱いになる。
     * 値が大きいほど重要度が低い（前面 = 100 が最小）ことに注意。
     */
    private fun fallbackIsBackgroundByImportance(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        val background = state.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
        Log.d(TAG, "fallback importance=${state.importance} background=$background")
        return background
    }
}
