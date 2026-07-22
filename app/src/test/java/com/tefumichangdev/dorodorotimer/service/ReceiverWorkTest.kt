package com.tefumichangdev.dorodorotimer.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ANR-06] ReceiverWork.heavyBlockingWork() の決定性を確認するテスト。
 * 純粋なJVM計算なので Robolectric 不要。
 * デモ既定値（PRIME_LIMIT）は数秒かかるため、テストは小さい上限でロジックの正しさのみ検証する。
 */
class ReceiverWorkTest {

    @Test
    fun heavyBlockingWork_returnsDeterministicPrimeCount() {
        // 10_000 以下の素数の個数は 1,229 個（数学的に確定した値）
        val result = ReceiverWork.heavyBlockingWork(limit = 10_000)
        assertEquals("10_000 以下の素数の個数が期待値と一致すること", 1_229L, result)
    }
}
