package com.tefumichangdev.dorodorotimer.feature.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationDigitsTest {

    // digitsToSeconds
    @Test
    fun digitsToSeconds_empty_returns0() {
        assertEquals(0, digitsToSeconds(""))
    }

    @Test
    fun digitsToSeconds_0130_returns90() {
        assertEquals(90, digitsToSeconds("0130"))
    }

    @Test
    fun digitsToSeconds_0059_returns59() {
        assertEquals(59, digitsToSeconds("0059"))
    }

    // secondsToDigits
    @Test
    fun secondsToDigits_90_returns0130() {
        assertEquals("0130", secondsToDigits(90))
    }

    @Test
    fun secondsToDigits_roundTrip_90() {
        val digits = secondsToDigits(90)
        assertEquals(90, digitsToSeconds(digits))
    }

    // formatDigits
    @Test
    fun formatDigits_130_returns01colon30() {
        assertEquals("01:30", formatDigits("130"))
    }

    // appendDigit
    @Test
    fun appendDigit_0005_9_returns0059_accepted() {
        assertEquals("0059", appendDigit("0005", 9))
    }

    @Test
    fun appendDigit_0059_9_returns0059_rejected() {
        // 下2桁が99になるので拒否 → buffer のまま返す
        assertEquals("0059", appendDigit("0059", 9))
    }

    @Test
    fun appendDigit_0012_5_returns0125_accepted() {
        assertEquals("0125", appendDigit("0012", 5))
    }
}
