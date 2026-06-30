package com.tefumichangdev.dorodorotimer.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [StatsLockHolder] のユニットテスト（ANR-03「Deeplink × ロック競合」）。
 *
 * - [heavyCompute] の決定値を検証（純粋計算の正しさ）。
 * - [acquireForForeground] がロック保持中の別スレッドの解放まで待つことを検証（相互排他）。
 *
 * テストの高速化のため [heavyCompute] は小さい iterations・sleepMs=0 で呼ぶ。
 * 相互排他の検証は [StatsLockHolder.lock] を直接使い、`Thread.sleep(300)` で保持時間を制御する。
 */
class StatsLockHolderTest {

    // ------------------------------------------------
    // heavyCompute の決定値テスト
    // ------------------------------------------------

    @Test
    fun heavyCompute_returnsDeterministicValue() {
        val n = 1_000
        // 0 + 1 + ... + (n-1) = n*(n-1)/2
        val expected = n.toLong() * (n - 1).toLong() / 2L
        assertEquals(expected, StatsLockHolder.heavyCompute(iterations = n, sleepMs = 0L))
    }

    @Test
    fun heavyCompute_zeroIterations_returnsZero() {
        assertEquals(0L, StatsLockHolder.heavyCompute(iterations = 0, sleepMs = 0L))
    }

    @Test
    fun heavyCompute_oneIteration_returnsZero() {
        assertEquals(0L, StatsLockHolder.heavyCompute(iterations = 1, sleepMs = 0L))
    }

    // ------------------------------------------------
    // 相互排他（held by）の順序性テスト
    // ------------------------------------------------

    /**
     * BG スレッドが [StatsLockHolder.lock] を保持している間は
     * [StatsLockHolder.acquireForForeground] が返らない（待たされる）ことを検証する。
     *
     * 手順:
     *  1. BG: synchronized(lock) でロック取得 → CountDownLatch で通知 → 300ms 保持 → フラグ ON → 解放
     *  2. Main（テストスレッド）: CountDownLatch 待ち → BG がロックを持っていることを確認してから acquireForForeground 呼び出し
     *  3. acquireForForeground が返ったとき、BG のフラグが ON（= BG が先に解放した）を検証
     *
     * CountDownLatch で BG ロック取得を確認してから呼ぶため flaky にならない。
     */
    @Test
    fun acquireForForeground_waitsUntilLockIsReleased() {
        val lockHeld = CountDownLatch(1)
        val bgReleased = AtomicBoolean(false)

        val bg = Thread {
            synchronized(StatsLockHolder.lock) {
                lockHeld.countDown()        // ロック取得をテストスレッドへ通知
                Thread.sleep(300)           // 300ms 保持（ANR-03 教材用の保持スレッドを模倣）
                bgReleased.set(true)        // ロック解放直前にフラグをセット
            }
        }
        bg.start()

        // BG がロックを取得するまで待つ（最大 500ms）
        assertTrue("BG should acquire lock within 500ms", lockHeld.await(500, TimeUnit.MILLISECONDS))

        // BG がロックを保持している状態で acquireForForeground を呼ぶ → 待たされる(held by)
        val result = StatsLockHolder.acquireForForeground()

        // acquireForForeground が返ったとき、BG はすでにロックを解放済みのはず
        assertTrue(
            "acquireForForeground must return only after BG releases the lock",
            bgReleased.get(),
        )
        assertEquals(0L, result)
        bg.join(1_000)
    }
}
