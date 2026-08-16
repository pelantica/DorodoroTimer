package com.pelantica.dorodorotimer.app.startup

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 「このプロセスは**誰に**起こされたか」「**前回どう死んだか**」を OS に問い合わせる小さな窓口。
 *
 * [ANR-05] の分岐条件そのもの。ANR-05 は「背面で起こされた起動だけが重くなる」という構図なので、
 * `Application.onCreate` の入口で **今が背面起動かどうか**を知る必要がある。
 * そんな API があるのか、というのが事例の面白いところで、実際に2つある:
 *
 * - [isBackgroundStart] … `ActivityManager.getHistoricalProcessStartReasons`（API 35+）
 * - [lastExitWasAnr]    … `ActivityManager.getHistoricalProcessExitReasons`（API 30+）
 *
 * ## なぜ背面起動だけを狙うのか（締切の非対称性）
 *
 * 前面起動には**入力ディスパッチ5秒**という短い番犬がいる（ANR-02 がそれを破る事例）。
 * 一方、背面起動＝ジョブやブロードキャストがプロセスを起こしただけの起動には、
 * そもそもユーザーの入力が無い。**入力の番犬は鳴かない**。
 * 代わりに `onCreate` を見張っているのは、AMS 側が `bindApplication` に張る締切ひとつだけ:
 *
 * ```java
 * // frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java
 * static final int BIND_APPLICATION_TIMEOUT = 15 * 1000 * Build.HW_TIMEOUT_MULTIPLIER;
 * ```
 *
 * これを破ると AMS は
 * `Process ProcessRecord{...} failed to complete startup` を Reason に ANR を立てる
 * （`ProcessRecord#bindApplicationTimedOut` → `AnrHelper`）。
 * ダイアログは出ない（前面に見せる相手がいない）ので、**無言で kill される**だけ。
 * ユーザーからは「バックグラウンドで何かが起きたらしいが何も見えない」、
 * 開発者からは ApplicationExitInfo（reason=6 / ANR）と Crashlytics でしか見えない。
 * これが事例の山場になる。
 *
 * ## 締切の実値
 *
 * `ro.hw_timeout_multiplier` が 1（検証エミュ・実機の既定）なら 15 秒。
 * エミュレータや低速端末ではこの prop が 2 以上に設定されていることがあり、その場合は締切も倍になる。
 * 再現しないときは `adb shell getprop ro.hw_timeout_multiplier` を最初に確認する。
 *
 * ## 実測（エミュ API 37 (emulator-5554) / `ro.hw_timeout_multiplier` 未設定＝1 / 2026-08-16）
 *
 * `Start proc` から `ANR in` までは3回とも**15.3〜15.4秒**で、`BIND_APPLICATION_TIMEOUT`
 * ＝15秒とぴったり一致した（締切に達した瞬間に打ち切られるので、onCreate が最後まで走った
 * 場合の合計 18.5 秒には届かない＝**残り約4秒を残して殺される**）。
 *
 * | 経路 | Start proc → ANR | 起動理由 |
 * | --- | --- | --- |
 * | WorkManager（20秒の初期遅延で自然発火） | 15.394s | `reason=5`（JOB） |
 * | WorkManager（`cmd jobscheduler run -f`） | 15.362s | `reason=5`（JOB） |
 * | AlarmManager（タイマー終了 → `TimerAlarmReceiver`） | 15.335s | `reason=0`（ALARM） |
 *
 * - ログは `ANR in com.pelantica.dorodorotimer` /
 *   `Reason: Process ProcessRecord{...} failed to complete startup` →
 *   直後に `Killing ... (adj 0): bg anr`。**ANR ダイアログは一切出ない**。
 * - `dumpsys activity exit-info` の最新レコード:
 *   `reason=6 (ANR) subreason=34 (BIND APPLICATION ANR)` /
 *   `description=bg anr: Process ProcessRecord{...} failed to complete startup` /
 *   `anrInfo=AnrInfo(..., isUserPerceptible=false)`。
 *   この **`isUserPerceptible=false` が「ダイアログを出さなかった」の一次証拠**になる。
 * - 次回の前面起動で Crashlytics が回収: `Persisting anr for session ...` →
 *   `Sending report through Google DataTransport` → `TRuntime.CctTransportBackend: Status Code: 200`。
 * - 同じフラグ構成のまま**前面**（ランチャー）起動は生き残る
 *   （`am start -W` の TotalTime 9993 / 10452 / 12484ms ＝ ANR-02 単独のときと同じ）。
 *
 * ## 実測で分かった「アラーム経路は即リトライされる」
 *
 * ブロードキャスト配送中に ANR 死すると、AMS は **50ms 後に同じブロードキャストを再配送**した
 * （`Killing 8095 ... bg anr` → `Start proc 8142 ... for broadcast {.../TimerAlarmReceiver}`）。
 * [lastExitWasAnr] の安全弁が無ければ、ここで2発目の ANR が起き、それが延々と続く。
 * 実測では2発目の onCreate が `lastExitWasAnr=true` を読んで重い初期化をスキップし、
 * プロセスは生き残ってブロードキャストを正常に処理した（ANR は**ちょうど1回**だけ）。
 */
internal object StartupOrigin {

    private const val TAG = "StartupOrigin"

    /**
     * このプロセスが**背面の仕掛け**（ジョブ / ブロードキャスト / アラーム）に起こされたか。
     *
     * 一次情報は `ActivityManager.getHistoricalProcessStartReasons(maxNum)`（API 35+）。
     * 戻り値は**新しい順**で、`maxNum = 1` なら「今まさに始まったこのプロセス」のレコードが返る
     * （AMS はプロセス生成を決めた時点、つまり `bindApplication` より前に記録するので、
     * `Application.onCreate` の中から読んでも既に載っている）。
     *
     * ## 判定に pid を使わない理由（実機で確認した挙動）
     *
     * `ApplicationStartInfo.getPid()` を `Process.myPid()` と突き合わせて
     * 「このレコードは本当に自分か」を確かめたくなるが、**COLD 起動のレコードは pid=0 のまま**
     * であることが実測で分かった（エミュ API 37 / `dumpsys activity start-info`。
     * WARM 起動のレコードには pid が入る）。pid を AND 条件にすると、狙っている
     * 「冷えたプロセスがジョブに起こされる」ケースだけが必ず false になり、事例が死ぬ。
     * よって**最新1件の reason だけ**を見る。
     *
     * ```
     * ApplicationStartInfo #1:
     *  pid=0 ... startupState=0 reason=JOB startType=COLD startComponent=SERVICE
     *  intent=Intent { cmp=com.pelantica.dorodorotimer/...JobInfoSchedulerService }
     * ```
     *
     * ## reason の割り当て（AOSP）
     *
     * `ApplicationStartInfoTracker` はプロセスを起こしたきっかけを分類する。
     * `BIND_JOB_SERVICE` 権限を持つサービス（＝`JobService` / WorkManager の `SystemJobService`）
     * の起動は [ApplicationStartInfo.START_REASON_JOB] になる。
     * `AlarmManager` からのブロードキャスト配送は [ApplicationStartInfo.START_REASON_ALARM]、
     * それ以外のブロードキャスト配送は [ApplicationStartInfo.START_REASON_BROADCAST]。
     * ランチャーやアプリ内遷移は `START_REASON_LAUNCHER` / `START_REASON_START_ACTIVITY` で、
     * どちらも「前面」側なので false になる。
     *
     * **ALARM を入れ忘れない**こと。実測（エミュ API 37）では、`AlarmManager` が
     * `TimerAlarmReceiver` を叩いて起こしたプロセスは `START_REASON_BROADCAST`(3) ではなく
     * **`START_REASON_ALARM`(0)** だった。JOB と BROADCAST だけを見ていると、
     * アプリで最も自然に起きる「タイマー終了アラームがプロセスを起こす」経路を丸ごと取り逃す。
     * 実測で観測した値: 前面ランチャー起動 = `11`(START_ACTIVITY) /
     * WorkManager 経由 = `5`(JOB) / タイマーのアラーム経由 = `0`(ALARM)。
     *
     * ## フォールバック
     *
     * API 35 未満・レコードが空・API が投げた場合は
     * `ActivityManager.getMyMemoryState()` の importance で代用する
     * （[ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE] 以上＝前面ではない）。
     * それも失敗したら **false**（＝前面扱い）を返す。誤って背面と判定して重くするより、
     * 誤って前面と判定して軽いまま起動する方が安全側だから。
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
     * 前回このパッケージのプロセスが **ANR で死んだ**か（[ApplicationExitInfo.REASON_ANR]）。
     *
     * `getHistoricalProcessExitReasons(packageName, pid = 0, maxNum = 1)` の最新1件を見るだけ。
     * `pid = 0` は「pid で絞らない」の意味で、Crashlytics が起動時に前回セッションの死因を
     * 拾いに行くときと同じ読み方（だから ANR-05 で発生した ANR は次回起動で回収・送信される）。
     * API 30 未満はこの API 自体が無いので false。
     *
     * ## これは安全弁である（ANR を起こす条件ではない）
     *
     * [com.pelantica.dorodorotimer.app.DorodoroApplication] では
     * `isBackgroundStart(...) && !lastExitWasAnr(...)` として使う。目的は2つ:
     *
     * 1. **無限ループを断つ**。背面 ANR で死ぬ → WorkManager はジョブを未完了とみなして
     *    再スケジュールする → また起こされる → また死ぬ、が延々と続きうる。
     *    「直前が ANR 死なら今回は重くしない」と決めておけば、鳴るのは**1回武装につき1発だけ**になる。
     * 2. **文鎮化を防ぐ**。将来この初期化が重くなりすぎて前面起動まで殺すようになっても、
     *    一度 ANR で死ねば次の起動は必ず軽い。デモ機が二度と開けなくなる事態を構造的に避ける。
     *
     * 副作用として、教材としての観測順序も綺麗になる:
     * 「背面で無言 kill → 次にユーザーが開くと**普通に**起動し、その裏で Crashlytics が
     * 前回の ANR を回収して送る」という、実アプリで実際に起きる流れがそのまま再現される。
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
