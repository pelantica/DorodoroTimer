package com.pelantica.dorodorotimer.data.local.stats

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * [StatsStore] のユニットテスト。検証するのは「重さ」ではなく **ロックの効き方**
 * ＝ ANR-03 の前提そのもの:
 *  - [StatsStore.warmUp] が走っている間、他スレッドの [StatsStore.awaitReady] は**進めない**
 *  - 初期化が終われば [StatsStore.awaitReady] は**一瞬で返る**
 *  - 保持時間は時間基準（指定時間より短く切り上がらない）
 *
 * 保持時間は本番の 12 秒ではなく [HOLD_MILLIS] を注入して軽量に回す。
 * スレッドの起動順に依存させないため、順序は [CountDownLatch] で決定的にする
 * （`Thread.sleep` で「たぶんもう握っただろう」に賭けると flaky になる）。
 */
class StatsStoreTest {

    @Before
    fun setUp() = StatsStore.resetForTest()

    @After
    fun tearDown() = StatsStore.resetForTest()

    @Test
    fun awaitReady_blocksWhileWarmUpHoldsTheLock_andIsReadyWhenReleased() {
        // ANR-03 の本体: warmUp が握っている間、awaitReady 側は1ミリも働けずに待たされる。
        val holdStarted = CountDownLatch(1)
        val warmUpThread = thread(name = "stats-store-warmup") {
            StatsStore.warmUp(minHoldMillis = HOLD_MILLIS) { holdStarted.countDown() }
        }
        // ロックを確実に握った後で取りに行く（この待ちが無いと、どちらが先に monitor を
        // 取るか非決定的になり、テストが揺れる）。
        assertTrue(
            "warmUp がロックを取得しなかった",
            holdStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
        )

        val startNanos = System.nanoTime()
        val ready = StatsStore.awaitReady()
        val blockedMillis = (System.nanoTime() - startNanos) / 1_000_000

        // 返ってきた時点で初期化済み＝解放されたのは warmUp が最後まで終わってから。
        assertTrue("解放は初期化完了後のはず", ready)
        // 実際に待たされたこと（素通りしていない）。保持開始からの経過ぶん余裕を見て半分で判定する。
        assertTrue(
            "awaitReady がブロックされていない（${blockedMillis}ms）",
            blockedMillis >= HOLD_MILLIS / 2,
        )
        warmUpThread.join(TIMEOUT_MILLIS)
    }

    @Test
    fun awaitReady_returnsImmediatelyAfterWarmUpCompleted() {
        // 初期化が済んだ後は「ただのフラグ読み」に戻る＝ANR-03 は起動直後の一瞬だけの窓。
        StatsStore.warmUp(minHoldMillis = HOLD_MILLIS)

        val startNanos = System.nanoTime()
        val ready = StatsStore.awaitReady()
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue(ready)
        assertTrue("初期化済みなら即返るはず（${elapsedMillis}ms）", elapsedMillis < HOLD_MILLIS / 2)
    }

    @Test
    fun warmUp_holdsForAtLeastTheRequestedDuration() {
        // 時間基準ループなので、端末が速くても保持時間は縮まない（＝再校正が要らない）。
        assertFalse("初期状態は未初期化", StatsStore.awaitReady())

        val startNanos = System.nanoTime()
        StatsStore.warmUp(minHoldMillis = HOLD_MILLIS)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue("指定時間より短い（${elapsedMillis}ms）", elapsedMillis >= HOLD_MILLIS)
        assertTrue(StatsStore.awaitReady())
        // 重い処理が実際に走った証拠（成果物が残っている）。
        assertNotNull(StatsStore.fingerprintForTest())
    }

    @Test
    fun warmUp_isNoOpOnceInitialized() {
        // 遅延シングルトンなので2度目は素通り＝2回目の呼び出しでロックを握り直さない。
        StatsStore.warmUp(minHoldMillis = HOLD_MILLIS)

        val startNanos = System.nanoTime()
        StatsStore.warmUp(minHoldMillis = HOLD_MILLIS)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue("2回目は初期化済みで即返るはず（${elapsedMillis}ms）", elapsedMillis < HOLD_MILLIS / 2)
    }

    // --- ここから [ANR-03][正版] warmUpReactive / readiness の検証 ---

    @Test
    fun warmUpReactive_setsReadinessTrue_withNegligibleHold() = runTest {
        // minHoldMillis=0 で軽量に完了経路だけを見る（awaitReady 系と違い、ここは
        // ロックの「効き方」ではなく「readiness が流れること」を検証するのが目的）。
        assertFalse(StatsStore.readiness.value)

        StatsStore.warmUpReactive(minHoldMillis = 0)

        assertTrue(StatsStore.readiness.value)
        assertTrue(StatsStore.awaitReady())
        assertNotNull(StatsStore.fingerprintForTest())
    }

    @Test
    fun warmUpReactive_isIdempotent_secondCallReturnsImmediately() = runTest {
        StatsStore.warmUpReactive(minHoldMillis = HOLD_MILLIS)
        assertTrue(StatsStore.readiness.value)

        val startNanos = System.nanoTime()
        StatsStore.warmUpReactive(minHoldMillis = HOLD_MILLIS)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue("2回目は初期化済みで即返るはず（${elapsedMillis}ms）", elapsedMillis < HOLD_MILLIS / 2)
    }

    @Test
    fun warmUpReactive_readinessFlipsOnlyAfterHeavyWorkCompletes() = runTest {
        // 「準備中は false のまま→完了で true」を、他スレッドを覗き見るのではなく
        // 「関数が返った時点で最低保持時間ぶん経過している」という事実で決定的に検証する
        // （= 完了前に readiness=true が立つ経路が無いことの間接証拠）。
        assertFalse(StatsStore.readiness.value)

        val startNanos = System.nanoTime()
        StatsStore.warmUpReactive(minHoldMillis = HOLD_MILLIS)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue(
            "readiness=true になったのは重い処理完了後のはず（${elapsedMillis}ms）",
            elapsedMillis >= HOLD_MILLIS,
        )
        assertTrue(StatsStore.readiness.value)
    }

    private companion object {
        /** テスト用の保持時間。本番の [StatsStore.INIT_WORK_MILLIS]（25秒）は長すぎるので短縮する。 */
        const val HOLD_MILLIS = 300L

        /** ラッチ待ち・join のタイムアウト。ここに掛かるのは実装が壊れているときだけ。 */
        const val TIMEOUT_MILLIS = 10_000L
    }
}
