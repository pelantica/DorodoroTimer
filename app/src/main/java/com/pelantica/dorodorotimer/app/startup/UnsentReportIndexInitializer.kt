package com.pelantica.dorodorotimer.app.startup

import android.util.Log
import java.security.MessageDigest

/**
 * [ANR-05] 7つ目の「SDK風」初期化。**背面で起こされた起動のときだけ**走る、
 * 「未送信レポートのインデックス再構築」。
 *
 * ## (a) 物語
 *
 * クラッシュ / ANR 報告 SDK は、端末に溜まった未送信レポートを送る前に、
 * ローカルの索引を作り直すことがある（何が未送信で、どれが重複で、どれが期限切れか）。
 * 溜まっている件数に比例して重くなり、**溜まるのは端末がしばらく放置されたときだけ**。
 * つまり「ジョブに起こされた背面起動」という、いちばん人目に付かない場面で
 * いちばん重くなるという性質を持つ。実アプリに実在する種類の挙動で、
 * デモ用の `Thread.sleep(15000)` のような作り物ではない。
 *
 * 呼び出し側（[com.pelantica.dorodorotimer.app.DorodoroApplication.onCreate]）からは
 * `UnsentReportIndexInitializer.init()` という無害な1行にしか見えないのも
 * ANR-02 の6つと同じで、これが事例の肝。
 *
 * ## (b) なぜ「背面だけ」なのか
 *
 * 番犬が違う。
 *
 * | 起動のしかた | onCreate を見張る締切 | 破ったとき |
 * | --- | --- | --- |
 * | 前面（ランチャー / Activity） | 入力ディスパッチ **5秒**（ユーザーが触れば） | ANR ダイアログが出る |
 * | 背面（ジョブ / ブロードキャスト） | `bindApplication` **15秒** ×`ro.hw_timeout_multiplier` | **ダイアログなしで無言 kill** |
 *
 * 背面起動には触るユーザーがいない＝入力の番犬はそもそも鳴かない。
 * 代わりに AMS 側の `BIND_APPLICATION_TIMEOUT`（15秒）だけが `onCreate` を見張っていて、
 * これを破ると `Process ... failed to complete startup` を Reason に ANR が立ち、
 * プロセスは何も表示せずに消える。詳しい根拠は [StartupOrigin] の KDoc。
 *
 * ANR-02（6つの初期化・合計6.6〜8.0秒）は前面5秒の締切は破るが、15秒には届かない。
 * そこにこの初期化が乗って初めて 15秒を越える。つまり**単独犯ではなく総量**という
 * ANR-02 の教訓が、締切の種類を変えてもう一度効いてくる形になっている。
 *
 * ## (c) 処方
 *
 * ANR-02 とまったく同じ。「`onCreate` は予約だけ。仕事をしない」＝
 * [StartupGate.runOnWorkerThread] と同じくワーカースレッドへ逃がす。
 * 索引の再構築を待っている相手は WorkManager のワーカーだけで、そのワーカーは
 * 最初から別スレッドにいる。メインで走らせる理由はどこにもない。
 * 「起動が遅いだけ」ではなく「起動が**完了しない**」に化けるのが背面の怖いところで、
 * 前面でギリギリ間に合っているコードほど、背面で初めて牙を剥く。
 *
 * ## (d) 再現レシピ
 *
 * 設定画面で master ON / ANR-02 ON / ANR-05 ON（ANR-03 は OFF）にして再起動しておく。
 * ⚠️ `am force-stop` は使わない（ジョブもアラームも消える）。プロセスを殺すのは `am kill`。
 *
 * ```bash
 * # 1) WorkManager 経路
 * #    Work は onCreate で武装され初期遅延20秒。アプリが生きたまま20秒経つと
 * #    その場で消化されてしまうので、**起動してから20秒以内に**背面へ落として殺す。
 * adb shell am start -n com.pelantica.dorodorotimer/.MainActivity   # ここで武装される
 * adb shell input keyevent KEYCODE_HOME
 * sleep 5                                   # cached に落ちるまで待つ（待たないと am kill が効かない）
 * adb shell am kill com.pelantica.dorodorotimer
 * adb shell pidof com.pelantica.dorodorotimer            # 空なら死亡
 * # 放っておけば残りの遅延で自然に発火する。すぐ試したいなら jobId を指定して強制実行:
 * adb shell dumpsys jobscheduler | grep -oE "androidx.work.systemjobscheduler:u0a[0-9]+/[0-9]+"
 * adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler \
 *   com.pelantica.dorodorotimer <jobId>     # -n（namespace）が無いと job が見つからない
 *
 * # 2) アラーム（ブロードキャスト）経路: タイマーを1分など短めにして開始 → HOME →
 * #    am kill → 終了時刻を待つ。AlarmManager が TimerAlarmReceiver を配送して起こす。
 * #    ⚠️ TimerAlarmReceiver は exported=false なので、`adb shell am broadcast` で
 * #    直接叩くことはできない（黙って result=0 で落ちるだけでプロセスも起きない）。
 * #    この経路を試すにはアプリ自身にアラームを張らせるしかない。
 *
 * # どちらも Start proc から約15秒後（＝締切ちょうど）に:
 * adb logcat | grep -E "ANR in|failed to complete startup|bg anr"
 * adb shell dumpsys activity exit-info com.pelantica.dorodorotimer   # reason=6 (ANR)
 * ```
 *
 * 脱出できなくなったら `adb shell pm clear com.pelantica.dorodorotimer`（フラグも消える）。
 * ただし [StartupOrigin.lastExitWasAnr] の安全弁があるので、ANR 死の直後の起動は
 * この初期化をスキップする＝普通に開ける。
 *
 * ## (e) 実機校正の記録（エミュ API 37 (emulator-5554) / 2026-08-16）
 *
 * - `adb shell getprop ro.hw_timeout_multiplier` は**空**＝1。つまり締切は素の 15 秒。
 * - 背面起動時の ANR-02（`StartupGate: all initializers done in ...ms (on-main)`）は
 *   実測 **7844 / 8019 / 8113 / 8330 / 8479ms**。ここに [WORK_MILLIS] = 10,500ms を足すと
 *   onCreate の合計は約 **18.5秒** で、15秒の締切を**約3.5秒の余裕を持って**越える。
 *   逆に前面（入力5秒）は ANR-02 だけで既に越えているので、この上乗せは前面の挙動を変えない。
 * - 実測の打ち切り時刻は `Start proc` から **15.335 / 15.362 / 15.394 秒**（3回・2経路）。
 *   締切ちょうどで殺されるので、この初期化は 10.5秒 のうち **約6.5秒しか走らないまま**終わる。
 *   つまり [WORK_MILLIS] は「15秒を越えさせる」ためのマージンであって、全部消費される値ではない。
 *   これ以上増やしても ANR までの時間は縮まない（締切は固定）。
 * - 観測されたログ: `ANR in com.pelantica.dorodorotimer` /
 *   `Reason: Process ProcessRecord{...} failed to complete startup` /
 *   `Killing ... (adj 0): bg anr`。**ANR ダイアログは出ない**。
 * - `dumpsys activity exit-info`: `reason=6 (ANR) subreason=34 (BIND APPLICATION ANR)` /
 *   `description=bg anr: Process ProcessRecord{...} failed to complete startup` /
 *   `isUserPerceptible=false`。
 * - 次回の前面起動で Crashlytics が回収して送信（`Status Code: 200`）。
 * - 同じフラグのまま前面（ランチャー）起動は生き残る
 *   （`am start -W` TotalTime 9993 / 10452 / 12484ms ＝ ANR-02 単独と同じ）。
 *
 * 端末が変われば [WORK_MILLIS] だけを再校正する。目安は
 * 「ANR-02 の実測（on-main のログ）＋ [WORK_MILLIS] が 15秒 × `ro.hw_timeout_multiplier`
 * を 3秒以上上回ること」。
 */
internal object UnsentReportIndexInitializer {

    private const val TAG = "UnsentReportIndexInit"

    /**
     * [ANR-05] メインを占有する時間。ラウンド数ではなく**時間**を基準にするのは、
     * この事例で意味があるのが「何回ハッシュしたか」ではなく「締切まで何秒残っているか」だから
     * （速い端末はハッシュを多く回すだけで、保持時間はこの値のまま＝端末が変わっても意味が変わらない。
     * [com.pelantica.dorodorotimer.data.local.stats.StatsStore.INIT_WORK_MILLIS] と同じ考え方）。
     * 校正の根拠はこのクラスの KDoc (e)。
     */
    internal const val WORK_MILLIS = 10_500L

    /**
     * 時間基準ループが経過時間を確認する間隔（ハッシュのラウンド数）。
     * 毎ラウンド [System.nanoTime] を読むと計時のコストが支配的になるので、この単位でまとめて回す。
     */
    private const val ROUNDS_PER_TIME_CHECK = 512

    private val seed = "dorodoro-unsent-report-index".toByteArray()

    /**
     * 再構築の成果物。実際には誰も使わないが、保持しておくことで
     * ハッシュチェーンがデッドコードとして消える余地を無くしている
     * （[StartupWork.hashChain] と同じ考え方）。
     */
    private var indexFingerprint: ByteArray? = null

    /** [ANR-05] 呼び出し側から見ると1行。中では [workMillis] のあいだメインを丸ごと占有する。 */
    fun init(workMillis: Long = WORK_MILLIS) { // [ANR-05]
        StartupWork.timed(TAG, "rebuildIndex") {
            indexFingerprint = rebuildIndex(workMillis)
        }
        Log.d(TAG, "index fingerprint size=${indexFingerprint?.size}")
    }

    /**
     * 索引の再構築の中身。`digest = SHA256(digest)` を **[workMillis] が経過するまで**回す。
     *
     * `Thread.sleep` を使わないのは ANR-02 / ANR-03 と同じ理由: 偽の重りではなく実際に CPU を焼くと、
     * トレース上でも「メインが本当に働いている」ことが見える（busy 側の症状として正しく出る）。
     * 時間源は [System.nanoTime]（単調・素の JVM で動くのでユニットテストからも使える。
     * `android.os.SystemClock` は Robolectric なしのユニットテストでは動かない）。
     */
    private fun rebuildIndex(workMillis: Long): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        var digest = seed
        val deadline = System.nanoTime() + workMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            repeat(ROUNDS_PER_TIME_CHECK) { digest = md.digest(digest) }
        }
        return digest
    }

    /** **テスト専用**。再構築の成果物が実際に残っているか（＝重い処理が走ったか）の覗き窓。 */
    internal fun fingerprintForTest(): ByteArray? = indexFingerprint

    /** **テスト専用**。`object` はプロセス内で状態を持ち越すため、テスト間で成果物を戻す。 */
    internal fun resetForTest() {
        indexFingerprint = null
    }
}
