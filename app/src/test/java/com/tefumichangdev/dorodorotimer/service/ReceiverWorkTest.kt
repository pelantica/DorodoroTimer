package com.tefumichangdev.dorodorotimer.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ANR-06] ReceiverWork.heavyBlockingWork() の決定性を確認するテスト。
 * 純粋なJVM計算なので Robolectric 不要。
 */
class ReceiverWorkTest {

    @Test
    fun heavyBlockingWork_returnsDeterministicPrimeCount() {
        // 1_000_000 以下の素数の個数は 78,498 個（数学的に確定した値）
        val result = ReceiverWork.heavyBlockingWork()
        assertEquals("1_000_000 以下の素数の個数が期待値と一致すること", 78_498L, result)
    }
}
