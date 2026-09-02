package com.pelantica.dorodorotimer.app.startup

import android.util.Log
import java.security.MessageDigest

/**
 * [ANR-05] 7つ目の「SDK風」初期化。背面で起こされた起動のときだけ走る
 * 「未送信レポートのインデックス再構築」。呼び出し側からは無害な1行にしか見えない。
 *
 * 背面起動には入力の番犬（5秒）がおらず、`onCreate` を見張るのは `bindApplication` の
 * 15秒締切だけ（詳細は [StartupOrigin]）。ANR-02 の6つでは届かず、ここに [WORK_MILLIS] が
 * 乗って初めて締切を越える。破るとダイアログなしの無言 kill。
 * 処方は ANR-02 と同じ「`onCreate` は予約だけ」＝ [StartupGate.runOnWorkerThread]。
 *
 * 再現手順は README を参照。⚠️ `am force-stop` はジョブ・アラームの仕掛けごと消えるので使わない。
 */
internal object UnsentReportIndexInitializer {

    private const val TAG = "UnsentReportIndexInit"

    /**
     * [ANR-05] メインを占有する時間。ラウンド数ではなく時間を基準にするので、
     * 速い端末はハッシュを多く回すだけで占有時間は変わらない。
     */
    internal const val WORK_MILLIS = 10_500L

    /** 時間基準ループが経過時間を確認する間隔。毎回 [System.nanoTime] を読むと計時が支配的になるため。 */
    private const val ROUNDS_PER_TIME_CHECK = 512

    private val seed = "dorodoro-unsent-report-index".toByteArray()

    /** 再構築の成果物。ハッシュチェーンがデッドコードとして消えるのを防ぐための置き場。 */
    private var indexFingerprint: ByteArray? = null

    /** [ANR-05] 呼び出し側から見ると1行。中では [workMillis] のあいだメインを丸ごと占有する。 */
    fun init(workMillis: Long = WORK_MILLIS) { // [ANR-05]
        StartupWork.timed(TAG, "rebuildIndex") {
            indexFingerprint = rebuildIndex(workMillis)
        }
        Log.d(TAG, "index fingerprint size=${indexFingerprint?.size}")
    }

    /**
     * 索引の再構築の中身。`digest = SHA256(digest)` を [workMillis] が経過するまで回す。
     * `Thread.sleep` ではなく実際に CPU を焼くので、トレースに「メインが本当に働いている」姿が出る。
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
