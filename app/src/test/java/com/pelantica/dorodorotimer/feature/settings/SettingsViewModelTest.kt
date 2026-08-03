package com.pelantica.dorodorotimer.feature.settings

import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoFlags
import com.pelantica.dorodorotimer.core.debug.DemoFlagsState
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

    @Test
    fun setAnr_requiresRestartAnr_setsRestartPromptFor() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = SettingsViewModel(fake)
        assertEquals(null, vm.state.value.restartPromptFor)

        vm.setAnr(Anr.ANR_02, true)

        assertEquals(true, Anr.ANR_02.requiresRestart)
        assertEquals(Anr.ANR_02, vm.state.value.restartPromptFor)
        // フラグ自体は再起動を待たず即座に永続化される
        assertTrue(vm.state.value.perAnr[Anr.ANR_02] ?: false)
    }

    @Test
    fun setAnr_nonRestartAnr_doesNotSetRestartPromptFor() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = SettingsViewModel(fake)

        vm.setAnr(Anr.ANR_06, true)

        assertEquals(false, Anr.ANR_06.requiresRestart)
        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun dismissRestartPrompt_clearsRestartPromptFor() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = SettingsViewModel(fake)
        vm.setAnr(Anr.ANR_07, true)
        assertEquals(Anr.ANR_07, vm.state.value.restartPromptFor)

        vm.dismissRestartPrompt()

        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun setAnr_requiresRestartAnr_thenDismiss_revertsFlagAndToggle() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = SettingsViewModel(fake)
        assertEquals(false, fake.isOn(Anr.ANR_02))

        vm.setAnr(Anr.ANR_02, true)
        assertTrue(vm.state.value.perAnr[Anr.ANR_02] ?: false)
        assertTrue(fake.isOn(Anr.ANR_02))

        vm.dismissRestartPrompt()

        // キャンセルしたので、永続化されたフラグも state のトグル表示も元の値(false)に戻る
        assertFalse(fake.isOn(Anr.ANR_02))
        assertFalse(vm.state.value.perAnr[Anr.ANR_02] ?: false)
        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun setAnr_requiresRestartAnr_thenConfirmRestart_keepsFlagAndToggle() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = SettingsViewModel(fake)

        vm.setAnr(Anr.ANR_02, true)
        vm.confirmRestartPrompt()

        // 再起動を選んだので、フラグも state のトグル表示も変更後の値(true)のまま維持される
        assertTrue(fake.isOn(Anr.ANR_02))
        assertTrue(vm.state.value.perAnr[Anr.ANR_02] ?: false)
        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun setAnr_nonRequiresRestartAnr_dismissRestartPrompt_doesNotAffectFlag() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = SettingsViewModel(fake)

        // requiresRestart == false なのでダイアログは出ず、restartPromptFor は null のまま
        vm.setAnr(Anr.ANR_06, true)
        assertEquals(null, vm.state.value.restartPromptFor)
        assertTrue(fake.isOn(Anr.ANR_06))

        // ダイアログが出ていない状態で dismissRestartPrompt が呼ばれても何も起きない（no-op）
        vm.dismissRestartPrompt()

        assertTrue(fake.isOn(Anr.ANR_06))
        assertTrue(vm.state.value.perAnr[Anr.ANR_06] ?: false)
        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun setMaster_false_clearsAllOnAnrToggles() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true).also {
            it.setOn(Anr.ANR_02, true)
            it.setOn(Anr.ANR_06, true)
        }
        val vm = SettingsViewModel(fake)

        vm.setMaster(false)

        // requiresRestart の真偽を問わず、ON だったものはすべてクリアされる
        assertFalse(fake.isOn(Anr.ANR_02))
        assertFalse(fake.isOn(Anr.ANR_06))
        assertFalse(vm.state.value.perAnr[Anr.ANR_02] ?: true)
        assertFalse(vm.state.value.perAnr[Anr.ANR_06] ?: true)
        assertFalse(vm.state.value.master)
    }

    @Test
    fun setMaster_false_withRequiresRestartAnrOn_setsRestartPromptForMasterOff() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true).also {
            it.setOn(Anr.ANR_02, true)
            it.setOn(Anr.ANR_06, true)
        }
        val vm = SettingsViewModel(fake)

        vm.setMaster(false)

        assertEquals(listOf(Anr.ANR_02), vm.state.value.restartPromptForMasterOff)
    }

    @Test
    fun setMaster_false_withOnlyNonRestartAnrsOn_doesNotSetRestartPromptForMasterOff() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true).also {
            it.setOn(Anr.ANR_06, true)
            it.setOn(Anr.ANR_FGS, true)
        }
        val vm = SettingsViewModel(fake)

        vm.setMaster(false)

        assertEquals(null, vm.state.value.restartPromptForMasterOff)
        // ダイアログが出ないケースでも、トグル自体は一括クリアされている
        assertFalse(fake.isOn(Anr.ANR_06))
        assertFalse(fake.isOn(Anr.ANR_FGS))
    }

    @Test
    fun setMaster_false_thenDismissMasterOffRestartPrompt_revertsMasterAndAllClearedToggles() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true).also {
            it.setOn(Anr.ANR_02, true)
            it.setOn(Anr.ANR_06, true)
        }
        val vm = SettingsViewModel(fake)
        vm.setMaster(false)
        assertEquals(listOf(Anr.ANR_02), vm.state.value.restartPromptForMasterOff)

        vm.dismissMasterOffRestartPrompt()

        // キャンセルしたので、マスターと requiresRestart でない ANR_06 も含めてすべて元(ON)に戻る
        assertTrue(fake.isMasterOn())
        assertTrue(fake.isOn(Anr.ANR_02))
        assertTrue(fake.isOn(Anr.ANR_06))
        assertTrue(vm.state.value.master)
        assertTrue(vm.state.value.perAnr[Anr.ANR_02] ?: false)
        assertTrue(vm.state.value.perAnr[Anr.ANR_06] ?: false)
        assertEquals(null, vm.state.value.restartPromptForMasterOff)
    }

    @Test
    fun setMaster_false_thenConfirmMasterOffRestartPrompt_keepsClearedState() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true).also {
            it.setOn(Anr.ANR_02, true)
            it.setOn(Anr.ANR_06, true)
        }
        val vm = SettingsViewModel(fake)
        vm.setMaster(false)

        vm.confirmMasterOffRestartPrompt()

        // 再起動を選んだので、マスターOFF・個別トグルクリア済みの状態のまま維持される
        assertFalse(fake.isMasterOn())
        assertFalse(fake.isOn(Anr.ANR_02))
        assertFalse(fake.isOn(Anr.ANR_06))
        assertFalse(vm.state.value.master)
        assertEquals(null, vm.state.value.restartPromptForMasterOff)
    }
}
