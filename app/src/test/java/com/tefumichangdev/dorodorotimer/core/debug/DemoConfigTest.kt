package com.tefumichangdev.dorodorotimer.core.debug

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DemoConfig のロジックテスト。Android 依存なしで JVM 上で動く。 */
class DemoConfigTest {

    private class FakeDemoFlags(
        private var master: Boolean = false,
        private val perAnr: MutableMap<Anr, Boolean> = mutableMapOf(),
    ) : DemoFlags {
        override fun isMasterOn(): Boolean = master
        override fun isOn(anr: Anr): Boolean = perAnr[anr] ?: false
        override fun setMaster(on: Boolean) { master = on }
        override fun setOn(anr: Anr, on: Boolean) { perAnr[anr] = on }
        override fun snapshot(): DemoFlagsState =
            DemoFlagsState(master = master, perAnr = Anr.entries.associateWith { perAnr[it] ?: false })
    }

    @After
    fun tearDown() {
        // テスト後は null に戻してクリーンな状態にする
        DemoConfig.setFlagsForTest(null)
    }

    @Test
    fun isOn_masterFalse_returnsFalse_evenIfIndividualTrue() {
        val fake = FakeDemoFlags(master = false)
        fake.setOn(Anr.ANR_01, true)
        DemoConfig.setFlagsForTest(fake)
        assertFalse(DemoConfig.isOn(Anr.ANR_01))
    }

    @Test
    fun isOn_masterTrue_individualTrue_returnsTrue() {
        val fake = FakeDemoFlags(master = true)
        fake.setOn(Anr.ANR_01, true)
        DemoConfig.setFlagsForTest(fake)
        assertTrue(DemoConfig.isOn(Anr.ANR_01))
    }

    @Test
    fun isOn_masterTrue_individualFalse_returnsFalse() {
        val fake = FakeDemoFlags(master = true)
        // ANR_01 は個別 false（デフォルト）
        DemoConfig.setFlagsForTest(fake)
        assertFalse(DemoConfig.isOn(Anr.ANR_01))
    }

    @Test
    fun isOn_uninit_returnsFalseWithoutCrash() {
        // flags = null の状態
        DemoConfig.setFlagsForTest(null)
        assertFalse(DemoConfig.isOn(Anr.ANR_01))
    }

    @Test
    fun enabled_get_masterTrue_returnsTrue() {
        val fake = FakeDemoFlags(master = true)
        DemoConfig.setFlagsForTest(fake)
        assertTrue(DemoConfig.enabled)
    }

    @Test
    fun enabled_set_propagatesToFlags() {
        val fake = FakeDemoFlags(master = false)
        DemoConfig.setFlagsForTest(fake)
        DemoConfig.enabled = true
        assertTrue(fake.isMasterOn())
    }
}
