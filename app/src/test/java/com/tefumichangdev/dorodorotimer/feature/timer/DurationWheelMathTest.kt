package com.tefumichangdev.dorodorotimer.feature.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationWheelMathTest {

    // secondsToWheelValues: 通常ケース
    @Test
    fun secondsToWheelValues_1500_returns25minutes0seconds() {
        assertEquals(25 to 0, secondsToWheelValues(1500, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    @Test
    fun secondsToWheelValues_alreadyStepAligned_isUnchanged() {
        // 12分30秒 = 750秒 はそのまま
        assertEquals(12 to 30, secondsToWheelValues(750, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    // secondsToWheelValues: 5の倍数でない値の丸め
    @Test
    fun secondsToWheelValues_roundsDownToNearestStep() {
        // 12分32秒 -> 30秒側が近い（|32-30|=2 < |32-35|=3）
        assertEquals(12 to 30, secondsToWheelValues(12 * 60 + 32, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    @Test
    fun secondsToWheelValues_roundsUpToNearestStep() {
        // 12分33秒 -> 35秒側が近い（|33-35|=2 < |33-30|=3）
        assertEquals(12 to 35, secondsToWheelValues(12 * 60 + 33, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    @Test
    fun secondsToWheelValues_58seconds_carriesToNextMinute() {
        // 12分58秒 -> 60に丸まるので 13分0秒 へキャリー
        assertEquals(13 to 0, secondsToWheelValues(12 * 60 + 58, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    @Test
    fun secondsToWheelValues_59seconds_carriesToNextMinute() {
        assertEquals(13 to 0, secondsToWheelValues(12 * 60 + 59, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    // secondsToWheelValues: レンジ外のクランプ（無効値でクラッシュ・無限ループしない）
    @Test
    fun secondsToWheelValues_zeroSeconds_clampedToMinMinutes() {
        assertEquals(FOCUS_MIN_MINUTES to 0, secondsToWheelValues(0, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    @Test
    fun secondsToWheelValues_negativeSeconds_clampedToMinMinutes() {
        assertEquals(FOCUS_MIN_MINUTES to 0, secondsToWheelValues(-100, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    @Test
    fun secondsToWheelValues_hugeMinutes_clampedToMaxMinutes() {
        val (minutes, seconds) = secondsToWheelValues(999 * 60, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES)
        assertEquals(FOCUS_MAX_MINUTES, minutes)
        assertEquals(0, seconds)
    }

    @Test
    fun secondsToWheelValues_carryPastMaxMinutes_clampedToMaxMinutes() {
        // 上限ちょうどの分で秒が繰り上がっても上限を超えない
        val (minutes, seconds) = secondsToWheelValues(FOCUS_MAX_MINUTES * 60 + 58, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES)
        assertEquals(FOCUS_MAX_MINUTES, minutes)
        assertEquals(0, seconds)
    }

    @Test
    fun secondsToWheelValues_breakRange_usesBreakBounds() {
        val (minutes, seconds) = secondsToWheelValues(999 * 60, BREAK_MIN_MINUTES, BREAK_MAX_MINUTES)
        assertEquals(BREAK_MAX_MINUTES, minutes)
        assertEquals(0, seconds)
    }

    // wheelValuesToSeconds
    @Test
    fun wheelValuesToSeconds_25min0sec_returns1500() {
        assertEquals(1500, wheelValuesToSeconds(25, 0))
    }

    @Test
    fun wheelValuesToSeconds_12min30sec_returns750() {
        assertEquals(750, wheelValuesToSeconds(12, 30))
    }

    @Test
    fun wheelValuesToSeconds_roundTrip_withStepAlignedValue() {
        val (minutes, seconds) = secondsToWheelValues(750, FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES)
        assertEquals(750, wheelValuesToSeconds(minutes, seconds))
    }

    // minutesWheelValues / SECONDS_WHEEL_VALUES
    @Test
    fun minutesWheelValues_focusRange_has120Entries() {
        val values = minutesWheelValues(FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES)
        assertEquals(120, values.size)
        assertEquals(1, values.first())
        assertEquals(120, values.last())
    }

    @Test
    fun minutesWheelValues_breakRange_has60Entries() {
        val values = minutesWheelValues(BREAK_MIN_MINUTES, BREAK_MAX_MINUTES)
        assertEquals(60, values.size)
        assertEquals(1, values.first())
        assertEquals(60, values.last())
    }

    @Test
    fun secondsWheelValues_hasTwelveStepsOfFive() {
        assertEquals(12, SECONDS_WHEEL_VALUES.size)
        assertEquals(listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55), SECONDS_WHEEL_VALUES)
    }
}
