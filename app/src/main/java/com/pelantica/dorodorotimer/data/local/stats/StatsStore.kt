package com.pelantica.dorodorotimer.data.local.stats

import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [ANR-03] 「最初に使う人が初期化コストを払う」遅延シングルトンの模型。
 *
 * 単体では正しく見える3つ（遅延シングルトン／重い初期化をメイン外へ／初期化済みなら一瞬で返る同期アクセス）
 * を組み合わせると事故る。[warmUp] が初期化の全時間 `synchronized(this)` を握るため、その最中に
 * メインが [awaitReady] を呼ぶと monitor 待ちで凍る。メインは1ミリも働いていない（waiting 系）。
 *
 * 処方は [warmUpReactive] / [readiness]（`[ANR-03][正版]`）。ロックを捨てるのではなく、
 * 待たせない仕組み（単一起動＋`StateFlow`）へ移す。再現手順は README を参照。
 */
internal object StatsStore {

    /** [ANR-03] 初期化がロックを握る時間。時間基準なので端末が変わっても保持時間は同じ。 */
    const val INIT_WORK_MILLIS = 25_000L

    /** 時間基準ループが経過時間を確認する間隔。毎回 [System.nanoTime] を読むと計時が支配的になるため。 */
    private const val ROUNDS_PER_TIME_CHECK = 512

    private val seed = "dorodoro-stats-store".toByteArray()

    /** 読み書きはすべて `synchronized(this)` の内側なので `@Volatile` は不要。 */
    private var isInitialized = false

    /** [heavyInitWork] の結果がデッドコードとして消えるのを防ぐための置き場。 */
    private var initFingerprint: ByteArray? = null

    /**
     * [ANR-03] ワーカースレッド側の入口。**ロックを握ったまま**重い初期化を最後まで実行する。
     * 逃がす判断自体は正しく、事故るのは逃がした先でロックを握り続けること。
     *
     * @param minHoldMillis ロックを保持する最小時間。テストは短い値を渡す。
     * @param onHoldStarted テスト専用フック。ロック取得後・初期化開始前に呼ばれ、
     *  「warmUp がロックを握った後に awaitReady を呼ぶ」順序を決定的にする。
     */
    fun warmUp(
        minHoldMillis: Long = INIT_WORK_MILLIS,
        onHoldStarted: () -> Unit = {},
    ) {
        // [ANR-03] 初期化の全時間ロックを握る。ここが事故の本体＝保持の粒度が荒すぎる。
        synchronized(this) {
            if (isInitialized) return
            onHoldStarted()
            initFingerprint = heavyInitWork(minHoldMillis)
            isInitialized = true
        }
    }

    /**
     * [ANR-03] 利用側（このデモではメイン）の入口。初期化中は [warmUp] が手放すまでブロックする。
     * 呼び出し側からは「フラグを1つ読むだけ」に見えるのが厄介なところ。
     */
    fun awaitReady(): Boolean = synchronized(this) { isInitialized }

    private val _readiness = MutableStateFlow(false)

    /** [ANR-03][正版] 準備完了。UI はこれを observe するだけで、[awaitReady] は呼ばない。 */
    val readiness: StateFlow<Boolean> = _readiness.asStateFlow()

    /** [warmUpReactive] の成果物。`synchronized` を通さないので可視性は `@Volatile` で担保する。 */
    @Volatile
    private var reactiveFingerprint: ByteArray? = null

    /**
     * [ANR-03][正版] 処方の実装。呼び出し側は `launch` するだけで誰も待たず、UI は [readiness] を
     * observe して Loading → Ready を描く。
     *
     * 自前ロックを持たない。必要な同期はブロックしない仕組みへ委譲している:
     * 相互排他は「Application から1回だけ launch する」構造で、可視性は `StateFlow` への書き込みで担保する。
     */
    suspend fun warmUpReactive(minHoldMillis: Long = INIT_WORK_MILLIS) {
        if (_readiness.value) return
        reactiveFingerprint = withContext(Dispatchers.Default) { heavyInitWork(minHoldMillis) }
        _readiness.value = true
    }

    /**
     * 「重い初期化」の中身。`digest = SHA256(digest)` を [minHoldMillis] が経過するまで回す。
     *
     * `Thread.sleep` ではなく実際に CPU を焼くのは、トレース上で「held by 側が働いている」ことを
     * 見せるため。ラウンド数ではなく時間を基準にするのは、この事例で意味があるのが保持時間だから。
     * 時間源が [System.nanoTime] なのは、素の JVM で動くユニットテストからも使えるため。
     */
    private fun heavyInitWork(minHoldMillis: Long): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        var digest = seed
        val deadline = System.nanoTime() + minHoldMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            repeat(ROUNDS_PER_TIME_CHECK) { digest = md.digest(digest) }
        }
        return digest
    }

    /** テスト専用。`object` は状態を持ち越すため、テスト間で初期化済みフラグを戻す。 */
    internal fun resetForTest() {
        synchronized(this) {
            isInitialized = false
            initFingerprint = null
        }
        reactiveFingerprint = null
        _readiness.value = false
    }

    /** テスト専用。 */
    internal fun fingerprintForTest(): ByteArray? = synchronized(this) { initFingerprint }

    /** テスト専用。 */
    internal fun reactiveFingerprintForTest(): ByteArray? = reactiveFingerprint
}
