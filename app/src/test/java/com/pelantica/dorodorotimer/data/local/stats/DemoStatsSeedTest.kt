package com.pelantica.dorodorotimer.data.local.stats

import com.pelantica.dorodorotimer.domain.model.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [ANR-01] [DemoStatsSeed]（BlockingStatsRepository / OffloadedStatsRepository 共通のシード仕様）を検証。
 *
 * ここで担保したいのは「同じ入力（rowCount・nowEpochMs）なら同じ行が出る」という決定性そのもの。
 * 両実装が公平に対比できるのは、この決定性がある前提で成り立つ。
 */
class DemoStatsSeedTest {

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun generate_isDeterministic_sameInputProducesSameRows() {
        val first = DemoStatsSeed.generate(rowCount = 10, nowEpochMs = fixedNow)
        val second = DemoStatsSeed.generate(rowCount = 10, nowEpochMs = fixedNow)

        assertEquals(first, second)
    }

    @Test
    fun generate_differentNow_producesDifferentTimestamps() {
        val first = DemoStatsSeed.generate(rowCount = 10, nowEpochMs = fixedNow)
        val second = DemoStatsSeed.generate(rowCount = 10, nowEpochMs = fixedNow + 86_400_000L)

        assertNotEquals(first, second)
    }

    @Test
    fun generate_producesExactlyRowCountRows() {
        val rows = DemoStatsSeed.generate(rowCount = 23, nowEpochMs = fixedNow)

        assertEquals(23, rows.size)
    }

    @Test
    fun generate_oneInFiveRowsIsBreak_restAreFocus() {
        val rows = DemoStatsSeed.generate(rowCount = 10, nowEpochMs = fixedNow)

        val breakRows = rows.filter { it.phase == TimerPhase.BREAK.name }
        val focusRows = rows.filter { it.phase == TimerPhase.FOCUS.name }
        assertEquals(2, breakRows.size)
        assertEquals(8, focusRows.size)
        assertEquals(true, breakRows.all { it.durationSeconds == 300 })
        assertEquals(true, focusRows.all { it.durationSeconds == 1500 })
    }

    @Test
    fun generate_spreadsRowsAcrossMultipleDays() {
        val rows = DemoStatsSeed.generate(rowCount = 30, nowEpochMs = fixedNow)

        val distinctDays = rows.map { it.completedAtEpochMs / 86_400_000L }.distinct()
        assertEquals(true, distinctDays.size > 1)
    }
}
