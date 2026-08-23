package com.pelantica.dorodorotimer.service

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ANR-FGS] [FgsStartupWork] の検証。狙いは「呼び出しスレッドを**要求時間以上**ブロックすること」。
 * 実機での5秒ルール違反そのものはユニットでは検証できないので、時間基準ループが確実に時間を
 * 消費する（＝端末性能に依らず締切を破れる）ことを緩く担保する。
 */
class FgsStartupWorkTest {

    @Test
    fun blockMainUntilDeadline_blocksAtLeastRequestedTime() {
        val holdMillis = 200L

        val startNanos = System.nanoTime()
        FgsStartupWork.blockMainUntilDeadline(blockMillis = holdMillis)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue(
            "要求時間以上ブロックするはず（${elapsedMillis}ms）",
            elapsedMillis >= holdMillis,
        )
    }
}
