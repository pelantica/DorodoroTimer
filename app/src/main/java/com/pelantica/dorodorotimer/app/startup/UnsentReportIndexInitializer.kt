package com.pelantica.dorodorotimer.app.startup

import android.util.Log
import java.security.MessageDigest

/**
 * [ANR-05] 7つ目の「SDK風」初期化。**背面で起こされた起動のときだけ**走る
 * 「未送信レポートのインデックス再構築」——溜まった未送信レポートの索引を作り直す、
 * 実アプリに実在する種類の挙動（溜まるのは放置されたときだけ＝いちばん人目に付かない
 * 背面起動でいちばん重くなる）。呼び出し側からは無害な1行にしか見えない。
 *
 * 背面起動には入力の番犬（5秒）がおらず、`onCreate` を見張るのは `bindApplication` の
 * **15秒**締切だけ（詳細は [StartupOrigin]）。ANR-02 の6つ（実測 7.8〜8.5秒）では届かず、
 * ここに [WORK_MILLIS] が乗って合計約18.5秒＝初めて締切を越える。**単独犯ではなく総量**
 * という ANR-02 の教訓の、締切を変えた再演。前面起動では走らないので前面の挙動は変わらない。
 *
 * 処方は ANR-02 と同じ「`onCreate` は予約だけ」＝ [StartupGate.runOnWorkerThread]。
 *
 * ## 再現
 * - WorkManager 経路: `./scripts/demo-anr05.sh`（1コマンド。README の連結レシピ参照）
 * - アラーム経路（adb 不要）: 短いタイマーを開始 → Recents からスワイプ終了 → 放置
 * - ⚠️ `am force-stop` はジョブ・アラームごと消えるので使わない。ANR 死直後の起動は
 *   [StartupOrigin.lastExitWasAnr] の安全弁で軽くなる＝普通に開ける。最終脱出は `pm clear`。
 *
 * ## 校正
 * ANR-02 実測 7.8〜8.5秒 + [WORK_MILLIS] 10.5秒 ≒ 18.5秒 > 締切15秒
 * （打ち切りは Start proc から 15.3〜15.4秒＝締切ちょうど。WORK_MILLIS は越えるための
 * マージンであり全部は消費されない）。端末が変わったら「ANR-02 実測 + WORK_MILLIS が
 * 15秒 × `ro.hw_timeout_multiplier` を3秒以上上回る」よう WORK_MILLIS だけ再校正する。
 */
internal object UnsentReportIndexInitializer {

    private const val TAG = "UnsentReportIndexInit"

    /**
     * [ANR-05] メインを占有する時間。ラウンド数ではなく**時間**を基準にするのは、
     * この事例で意味があるのが「何回ハッシュしたか」ではなく「締切まで何秒残っているか」だから
     * （速い端末はハッシュを多く回すだけで、保持時間はこの値のまま＝端末が変わっても意味が変わらない。
     * [com.pelantica.dorodorotimer.data.local.stats.StatsStore.INIT_WORK_MILLIS] と同じ考え方）。
     * 校正の手順はこのクラスの KDoc「校正」。
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
