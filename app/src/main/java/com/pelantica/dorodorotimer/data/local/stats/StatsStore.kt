package com.pelantica.dorodorotimer.data.local.stats

import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [ANR-03] 「最初に使う人が初期化コストを払う」遅延シングルトンの模型。ANR-03（waiting系）の現場。
 *
 * ## 構図（なぜ正しそうなコード3つで事故るのか）
 * 登場するコードはどれも単体では**正しそうに見える**:
 *  1. 遅延シングルトン（使う前に一度だけ初期化する）
 *  2. 重い初期化はメインスレッドの外へ逃がす（[warmUp] をワーカースレッドから呼ぶ）
 *  3. 初期化済みのシングルトンに同期アクセスする（[awaitReady]＝「もう済んでいるから一瞬」）
 *
 * 事故るのは**ロックの粒度**と**メインが monitor を取りに行くこと**の組み合わせ。
 * [warmUp] は初期化の**全時間** `synchronized(this)` を握り続けるので、その最中に
 * メインが [awaitReady] を呼ぶと monitor 待ちに入り、**1ミリも働かないまま**凍る。
 * ANR-01（busy＝メイン自身が重い仕事をして固まる）との対比がここ:
 * ANR-03 のメインは仕事をしていない。ただ**待たされている**。
 *
 * トレースの見え方（waiting 系の指紋）:
 * ```
 * "main" prio=5 tid=1 Blocked
 *   at com.pelantica.dorodorotimer.data.local.stats.StatsStore.awaitReady(StatsStore.kt)
 *   - waiting to lock <0x0a1b2c3d> (a com.pelantica...StatsStore) held by thread 12 ("stats-store-warmup")
 * ```
 * `Blocked` ＋ `waiting to lock ... held by "スレッド名"` が出たら、読むべきは main ではなく
 * **held by 側のスタック**（そこに本当の重い処理がいる）。スレッドに名前を付けておくと
 * ここで犯人が一目で分かる（[com.pelantica.dorodorotimer.app.DorodoroApplication] 参照）。
 *
 * なお本物の統計ストアなら、ここに保持されるのは DB ハンドルやコネクションプールといった
 * **実体**になる。教材なので「初期化が済んだか」を表す [isInitialized] フラグ
 * （＋ 実作業の成果物 [initFingerprint]）で代用している。構図は同じ。
 *
 * ## 処方
 *  - **メインから同期アクセスしない**: 待つ必要があるなら `suspend` にして
 *    `withContext(Dispatchers.IO) { awaitReady() }` で待つ。待つのはワーカー、メインは描き続ける。
 *  - **初期化とロック保持を分離する**: 重い処理はロックの**外**で走らせ、ロック内は
 *    出来上がった実体の代入だけにする（保持時間をミリ秒未満に落とす）。
 *  - **そもそもメインがロックに触る経路を作らない**: このアプリの正版（ANR-03 トグル OFF）は
 *    [awaitReady] を呼ばない。UI は ViewModel/Flow 経由で「準備できたら流れてくる」形にする。
 *
 * ## 再現レシピ
 *  1. 設定画面で demoMode マスター ＋ ANR-03 を ON にする
 *  2. アプリを**完全終了**する（`adb shell am force-stop com.pelantica.dorodorotimer`）。
 *     冷えたプロセスであることが前提＝起動と同時に [warmUp] が走る
 *  3. `adb shell am start -a android.intent.action.VIEW -d dorodoro://stats com.pelantica.dorodorotimer`
 *  4. 統計画面が**一瞬描かれた直後**に凍る（[com.pelantica.dorodorotimer.feature.stats.StatsScreen]
 *     が初回フレームを待ってから [awaitReady] を呼ぶため）
 *  5. `adb shell input keyevent KEYCODE_DPAD_CENTER` を1発送る
 *     （凍結中の keyevent は配達完了まで**ブロックして返ってこない**ので、スクリプトからは
 *     バックグラウンド実行にすること。また StrictMode バナーが出ているとフォーカスが
 *     バナーに乗り、ANR の契機が KeyEvent でなく FocusEvent になる。帰属は同じ自アプリだが、
 *     スライドで `Waited 5000ms for KeyEvent` を見せたいならバナーをミュートしておく）
 *  6. 約5秒で ANR 判定（Input dispatching timed out）。ダイアログの表示はそこから
 *     さらに +1〜11秒遅れる（AMS が全プロセスのスタックを採取してから出すため）
 *  7. ダイアログで「アプリを閉じる」を選ぶと kill され、`ApplicationExitInfo` に
 *     `reason=ANR` が記録される。次回起動時に Crashlytics がそれを回収する。
 *     ⚠️ ダイアログは**アプリが復帰（ロック解放）すると自動で消える**ので、
 *     保持時間中に閉じ切る必要がある（下の設計根拠が 25 秒である理由）
 *
 * ## 保持時間 25 秒の設計根拠（2026-08-16 実測で再校正済み。初版の12秒は不足だった）
 * 入力ディスパッチの締切は5秒だが、締切の計測が始まるのは**ユーザーが操作した瞬間**から。
 * さらに実測では、凍結開始までと ANR ダイアログ表示までに以下の遅延が積み上がる:
 * deeplink 着弾→統計画面表示が 1.5〜5.4秒（ばらつき大）、操作→ANR 判定が 5秒、
 * 判定→ダイアログ表示が +1〜11秒（AMS のスタック採取待ち）、そしてダイアログは
 * アプリ復帰と同時に自動で消える。12秒だと画面表示の時点で残り約6秒しかなく、
 * ダイアログが出る前後にロックが解放されて操作猶予がほぼ残らなかった。
 * 25秒なら「凍結に気づく → タップ → ダイアログが出る → 閉じるを押す」までが
 * すべて保持中に収まる（最悪ケースの積算 5.4+2+5+11≒23秒 に余裕を上乗せ）。
 *
 * ## 実機校正の記録
 * - 端末 / 日付: emulator-5554 `sdk_gphone16k_arm64` API 37 / 2026-08-16
 * - warmUp 実測保持時間: 設計値どおり（時間基準ループのため端末差なし）
 * - deeplink 着弾 → 統計画面が描かれるまで: 1.5〜5.4秒（`Displayed +1s540ms`〜`+5s436ms`）
 * - 操作 → ANR 判定: 5.00秒（`Waited 5000ms/5001ms`）
 * - ANR 判定 → ダイアログ表示: +1〜11秒（エミュでは AMS の全プロセス trace 採取が重い）
 * - 凍結中の指紋（`ps -T`）: main=S（monitor 待ち）/ stats-store-warmup=R（CPU 焼き続け）
 * - 帰属: 5回中5回 `ANR in com.pelantica.dorodorotimer`（ランチャー帰属ゼロ）。
 *   AEI reason=6 記録・次回起動で Crashlytics 送信（HTTP 200）まで一気通貫を確認
 * - ⚠️ 実機（Pixel 等）での再測は登壇前に一度やること（上記は全てエミュ実測）
 */
internal object StatsStore {

    /**
     * [ANR-03] 初期化がロックを握り続ける時間（ミリ秒）。根拠はこのクラスの KDoc「保持時間 25 秒の設計根拠」。
     * 端末が変わっても意味が変わらない（[heavyInitWork] が時間基準のループなので、
     * 速い端末はハッシュを多く回すだけで、保持時間はこの値のまま）。
     */
    const val INIT_WORK_MILLIS = 25_000L

    /**
     * 時間基準ループが経過時間を確認する間隔（ハッシュのラウンド数）。
     * 毎ラウンド [System.nanoTime] を読むと計時のコストが支配的になるので、この単位でまとめて回す。
     */
    private const val ROUNDS_PER_TIME_CHECK = 512

    private val seed = "dorodoro-stats-store".toByteArray()

    /**
     * 初期化が済んだか。本物なら DB ハンドル等の実体が入る場所（このクラスの KDoc 参照）。
     * 読み書きはすべて `synchronized(this)` の内側なので `@Volatile` は不要。
     */
    private var isInitialized = false

    /**
     * 初期化の成果物。実際には誰も使わないが、[heavyInitWork] の結果を保持しておくことで
     * ハッシュチェーンがデッドコードとして消える余地を無くしている
     * （[com.pelantica.dorodorotimer.app.startup.StartupWork.hashChain] と同じ考え方）。
     */
    private var initFingerprint: ByteArray? = null

    /**
     * [ANR-03] ワーカースレッド側の入口。**ロックを握ったまま**重い初期化を最後まで実行する。
     *
     * 「重い初期化をメインの外へ逃がす」という判断自体は正しい（ANR-02 の処方そのもの）。
     * 問題は逃がした先で `synchronized` を [minHoldMillis] のあいだ握り続けること。
     * このメソッドが走っている間、[awaitReady] を呼んだスレッドは全員止まる。
     *
     * 処方どおりに直すなら、[heavyInitWork] を `synchronized` の**外**で走らせ、
     * ロック内は `initFingerprint = result; isInitialized = true` の代入だけにする。
     *
     * @param minHoldMillis ロックを保持する最小時間。テストは短い値を渡して軽量に検証する。
     * @param onHoldStarted **テスト専用の順序制御フック**。ロックを取得し、重い初期化を始める
     *  直前に呼ばれる。テストが「warmUp が確実にロックを握った後で awaitReady を呼ぶ」順序を
     *  決定的にするためだけに存在する（本番の呼び出しは既定の何もしない実装のまま）。
     */
    fun warmUp(
        minHoldMillis: Long = INIT_WORK_MILLIS,
        onHoldStarted: () -> Unit = {},
    ) {
        // [ANR-03] 初期化の全時間ロックを握る。ここが事故の本体＝保持時間の粒度が荒すぎる。
        synchronized(this) {
            if (isInitialized) return
            onHoldStarted()
            initFingerprint = heavyInitWork(minHoldMillis)
            isInitialized = true
        }
    }

    /**
     * [ANR-03] 利用側（このデモではメインスレッド）の入口。
     * 初期化済みなら一瞬で返り、初期化中なら [warmUp] が手放すまで monitor 待ちでブロックする。
     *
     * 呼び出し側から見ると「フラグを1つ読むだけ」に見えるのが厄介なところ。
     * 重い処理は1行も書かれていないのに、メインがここで十数秒止まる。処方はこのクラスの KDoc。
     *
     * @return 初期化済みなら true（[warmUp] を待たされた場合、返る時点では必ず true）。
     */
    fun awaitReady(): Boolean = synchronized(this) { isInitialized }

    /**
     * [ANR-03][正版] 準備完了を表す Flow。UI はこれを observe するだけで良い（[awaitReady] は呼ばない）。
     * ON 経路と同じ「初期化済みか」を表すが、**流れてくる**側なので誰も待たない。
     */
    private val _readiness = MutableStateFlow(false)

    /** [ANR-03][正版] [_readiness] の読み取り専用ビュー。Compose からは `collectAsState()` で observe する。 */
    val readiness: StateFlow<Boolean> = _readiness.asStateFlow()

    /**
     * [ANR-03][正版] [warmUpReactive] の成果物の置き場。誰も読まないが、[heavyInitWork] の結果が
     * デッドコードとして消えるのを防ぐために保持する（ON 版の [initFingerprint] と同じ考え方）。
     * ON 版と違い `synchronized` を通さないので、可視性は `@Volatile` で単独に担保する。
     */
    @Volatile
    private var reactiveFingerprint: ByteArray? = null

    /**
     * [ANR-03][正版] このクラスの KDoc「処方」を実装したもの。呼び出し側（Application）は
     * `launch` するだけで**誰も待たない**。UI（StatsScreen）は [readiness] を observe して
     * Loading → Ready を描くだけで、[awaitReady] のような同期アクセスは一切行わない。
     *
     * **自前ロックを持たない**（NIA 流）。ロック競合 ANR の教訓は「ロックを使うな」ではなく
     * 「メインをロックで**待たせるな**」なので、必要な同期は捨てずに“ブロックしない仕組み”へ委譲する:
     *  - 相互排他（初期化を1回だけ）: `synchronized` ではなく「Application.onCreate から**ただ1回**
     *    launch する」という**構造**で保証する（[_readiness] での早期 return は二度呼びの冪等ガード）。
     *  - メモリ可視性（結果の公開）: [_readiness] への書き込み（`StateFlow`＝内部は atomic）が observe 側へ
     *    happens-before を張る。DCE 回避用の [reactiveFingerprint] は `@Volatile` で単独に公開する。
     * ＝ ON 版（[warmUp]/[awaitReady] が `synchronized`）と違い、正版は monitor に一切触れない。
     * 「ロックを消した」のではなく、待たせない仕組み（StateFlow・単一起動）へ**移した**のがポイント。
     *
     * 重い初期化 [heavyInitWork] は当然 [Dispatchers.Default] へ逃がす（メインは元から触れない）。
     *
     * @param minHoldMillis [heavyInitWork] が最低どれだけ回るか。既定は ON 版と同じ
     *  [INIT_WORK_MILLIS]（25秒）だが、これは教材用の重り＝本物の初期化なら一瞬で終わる
     *  はずのもの（TODO(製品化): 本物の統計ストア初期化に置き換える際はこの重りごと消す）。
     */
    suspend fun warmUpReactive(minHoldMillis: Long = INIT_WORK_MILLIS) {
        if (_readiness.value) return
        reactiveFingerprint = withContext(Dispatchers.Default) { heavyInitWork(minHoldMillis) }
        _readiness.value = true
    }

    /**
     * [ANR-03] 「重い初期化」の中身。`digest = SHA256(digest)` を **[minHoldMillis] が経過するまで**回す。
     *
     * `Thread.sleep` を使わないのは ANR-02 と同じ理由: 偽の重りではなく実際に CPU を焼く作業にすると、
     * トレース上でも「held by 側が本当に働いている」ことが見える。
     * ラウンド数ではなく**時間**を基準にするのは、この事例で意味があるのが「何回計算したか」ではなく
     * 「何秒ロックを握ったか」だから（端末が速くても保持時間は変わらない＝校正が要らない）。
     *
     * 時間源は [System.nanoTime]（単調・素の JVM で動くのでユニットテストからも使える。
     * `android.os.SystemClock` は Robolectric なしのユニットテストでは動かない）。
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

    /**
     * **テスト専用**。`object` はプロセス内で状態を持ち越すため、テスト間で初期化済みフラグを戻す。
     * 本番コードから呼んではいけない。
     */
    internal fun resetForTest() {
        synchronized(this) {
            isInitialized = false
            initFingerprint = null
        }
        reactiveFingerprint = null
        _readiness.value = false
    }

    /** **テスト専用**。ON 版 [warmUp] の成果物（`synchronized` 越し）を覗く。 */
    internal fun fingerprintForTest(): ByteArray? = synchronized(this) { initFingerprint }

    /** **テスト専用**。正版 [warmUpReactive] の成果物（`@Volatile`）を覗く。重い処理が走ったかの確認用。 */
    internal fun reactiveFingerprintForTest(): ByteArray? = reactiveFingerprint
}
