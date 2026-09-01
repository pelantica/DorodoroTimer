package com.pelantica.dorodorotimer.app.startup

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 「このプロセスは誰に起こされたか」「前回どう死んだか」を OS に問い合わせる小さな窓口。
 * [ANR-05] の分岐条件（背面起動だけ重くする＋安全弁）に使う。
 *
 * 背面起動には入力が無く、`onCreate` を見張る締切は `bindApplication` の
 * 15秒（× `ro.hw_timeout_multiplier`）ひとつだけ。破るとダイアログなしで無言 kill される。
 */
internal object StartupOrigin {

    private const val TAG = "StartupOrigin"

    /**
     * このプロセスが背面の仕掛け（ジョブ / ブロードキャスト / アラーム）に起こされたか。
     * API 35+ は `getHistoricalProcessStartReasons(1)` の最新1件の reason で判定する。
     * pid 照合はしない（COLD 起動のレコードは pid=0 のままで、照合すると必ず false になる）。
     * 判定できないときは false（前面扱い）＝誤って重くするより軽いまま起動する方が安全側。
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
     * 前回このパッケージのプロセスが ANR で死んだか。API 30 未満は false。
     * 用途は安全弁: 背面 ANR 死 → ジョブ再スケジュール → また ANR、の無限ループを断ち、
     * ANR 死の次の起動は必ず軽くする（文鎮化防止）。
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
     * タイマー終了アラームの起動は BROADCAST ではなく ALARM と記録されるため、ALARM も含める。
     */
    internal fun isBackgroundStartReason(reason: Int): Boolean = when (reason) {
        ApplicationStartInfo.START_REASON_JOB,
        ApplicationStartInfo.START_REASON_BROADCAST,
        ApplicationStartInfo.START_REASON_ALARM,
        -> true

        else -> false
    }

    /**
     * [ApplicationStartInfo] が使えないときの代用。「起動のきっかけ」ではなく「今の重要度」で近似する。
     * importance は値が大きいほど重要度が低い（前面 = 100 が最小）ことに注意。
     */
    private fun fallbackIsBackgroundByImportance(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        val background = state.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
        Log.d(TAG, "fallback importance=${state.importance} background=$background")
        return background
    }
}
