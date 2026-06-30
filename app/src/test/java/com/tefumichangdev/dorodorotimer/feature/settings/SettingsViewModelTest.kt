package com.tefumichangdev.dorodorotimer.feature.settings

import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoFlags
import com.tefumichangdev.dorodorotimer.core.debug.DemoFlagsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun setMaster_true_stateMasterBecomesTrue() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = false)
        val vm = SettingsViewModel(fake)
        assertFalse(vm.state.value.master)
        vm.setMaster(true)
        assertTrue(vm.state.value.master)
    }

    @Test
    fun setAnr_true_statePerAnrUpdated() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = SettingsViewModel(fake)
        assertFalse(vm.state.value.perAnr[Anr.ANR_01] ?: false)
        vm.setAnr(Anr.ANR_01, true)
        assertTrue(vm.state.value.perAnr[Anr.ANR_01] ?: false)
    }

    @Test
    fun setMaster_false_stateMasterBecomesFalse() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = SettingsViewModel(fake)
        assertTrue(vm.state.value.master)
        vm.setMaster(false)
        assertFalse(vm.state.value.master)
    }

    @Test
    fun initialState_reflectsFlags() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true).also { it.setOn(Anr.ANR_02, true) }
        val vm = SettingsViewModel(fake)
        assertEquals(true, vm.state.value.master)
        assertEquals(true, vm.state.value.perAnr[Anr.ANR_02])
    }
}
