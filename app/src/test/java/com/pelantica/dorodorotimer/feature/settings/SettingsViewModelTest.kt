package com.pelantica.dorodorotimer.feature.settings

import androidx.lifecycle.viewModelScope
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.core.debug.DemoFlags
import com.pelantica.dorodorotimer.core.debug.DemoFlagsState
import com.pelantica.dorodorotimer.domain.repository.SecuritySettingsRepository
import com.pelantica.dorodorotimer.vendor.securevault.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

private class FakeSecuritySettings(private var enabled: Boolean = false) : SecuritySettingsRepository {
    /** 最後に保存された値（未保存なら null）。 */
    var savedValue: Boolean? = null
    override suspend fun isEncryptFocusRecordsEnabled(): Boolean = enabled
    override suspend fun setEncryptFocusRecordsEnabled(enabled: Boolean) {
        this.enabled = enabled
        savedValue = enabled
    }
}

/**
 * [SecureVault] のテスト用実装。Binder は起こさず、**どのスレッドから呼ばれたか**だけを記録する
 * （ANR-04 の争点は「待つのが誰か」なので、それが検証対象）。
 */
private class FakeSecureVault : SecureVault {
    @Volatile var callCount = 0
    @Volatile var callingThreadName: String? = null
    var bindCount = 0
    var unbindCount = 0

    override fun bind() { bindCount++ }
    override fun unbind() { unbindCount++ }
    override fun generateKeyBlocking(alias: String): String {
        callCount++
        callingThreadName = Thread.currentThread().name
        return "fake-key-material"
    }
}

/**
 * ViewModel が起こしたコルーチンを最後まで見届ける。
 * 正版の経路は `withContext(Dispatchers.IO)` で**実スレッド**へ跳ぶので、join せずにテストを
 * 終えると IO から Main へ戻る再開が `resetMain()` の後に走り、後続のテストを巻き添えにする。
 */
private suspend fun SettingsViewModel.joinPendingWork() =
    viewModelScope.coroutineContext.job.children.toList().joinAll()

class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        Dispatchers.resetMain()
        // DemoConfig はプロセス内シングルトン。テスト間にフラグを持ち越さない。
        DemoConfig.setFlagsForTest(null)
    }

    private fun viewModelWith(
        flags: DemoFlags,
        securitySettings: SecuritySettingsRepository = FakeSecuritySettings(),
        vault: SecureVault = FakeSecureVault(),
    ) = SettingsViewModel(flags, securitySettings, vault)

    @Test
    fun setMaster_true_stateMasterBecomesTrue() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = false)
        val vm = viewModelWith(fake)
        assertFalse(vm.state.value.master)
        vm.setMaster(true)
        assertTrue(vm.state.value.master)
    }

    @Test
    fun setAnr_true_statePerAnrUpdated() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)
        assertFalse(vm.state.value.perAnr[Anr.ANR_01] ?: false)
        vm.setAnr(Anr.ANR_01, true)
        assertTrue(vm.state.value.perAnr[Anr.ANR_01] ?: false)
    }

    @Test
    fun setMaster_false_stateMasterBecomesFalse() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)
        assertTrue(vm.state.value.master)
        vm.setMaster(false)
        assertFalse(vm.state.value.master)
    }

    @Test
    fun initialState_reflectsFlags() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true).also { it.setOn(Anr.ANR_02, true) }
        val vm = viewModelWith(fake)
        assertEquals(true, vm.state.value.master)
        assertEquals(true, vm.state.value.perAnr[Anr.ANR_02])
    }

    @Test
    fun setAnr_requiresRestartAnr_setsRestartPromptFor() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)
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
        val vm = viewModelWith(fake)

        vm.setAnr(Anr.ANR_06, true)

        assertEquals(false, Anr.ANR_06.requiresRestart)
        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun dismissRestartPrompt_clearsRestartPromptFor() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)
        vm.setAnr(Anr.ANR_07, true)
        assertEquals(Anr.ANR_07, vm.state.value.restartPromptFor)

        vm.dismissRestartPrompt()

        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun setAnr_requiresRestartAnr_thenDismiss_revertsFlagAndToggle() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)
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
        val vm = viewModelWith(fake)

        vm.setAnr(Anr.ANR_02, true)
        vm.confirmRestartPrompt()

        // 再起動を選んだので、フラグも state のトグル表示も変更後の値(true)のまま維持される
        assertTrue(fake.isOn(Anr.ANR_02))
        assertTrue(vm.state.value.perAnr[Anr.ANR_02] ?: false)
        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun setMaster_false_clearsAllPerAnrToggles() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)
        Anr.entries.forEach { vm.setAnr(it, true) }
        Anr.entries.forEach { assertTrue(it.name, fake.isOn(it)) }

        vm.setMaster(false)

        assertFalse(vm.state.value.master)
        Anr.entries.forEach { anr ->
            assertFalse(anr.name, fake.isOn(anr))
            assertFalse(anr.name, vm.state.value.perAnr[anr] ?: false)
        }
    }

    @Test
    fun setMaster_false_clearsBothRestartAndNonRestartAnrs() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)
        // requiresRestart が true の事例と false の事例を混在させる
        vm.setAnr(Anr.ANR_02, true)
        vm.setAnr(Anr.ANR_06, true)
        assertEquals(true, Anr.ANR_02.requiresRestart)
        assertEquals(false, Anr.ANR_06.requiresRestart)

        vm.setMaster(false)

        assertFalse(fake.isOn(Anr.ANR_02))
        assertFalse(fake.isOn(Anr.ANR_06))
        assertFalse(vm.state.value.perAnr[Anr.ANR_02] ?: false)
        assertFalse(vm.state.value.perAnr[Anr.ANR_06] ?: false)
    }

    @Test
    fun setMaster_false_doesNotSetRestartPromptFor() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)
        vm.setAnr(Anr.ANR_02, true)
        vm.confirmRestartPrompt()
        assertEquals(null, vm.state.value.restartPromptFor)

        // requiresRestart な事例を巻き込んでクリアしても、再起動ダイアログは出さない
        vm.setMaster(false)

        assertEquals(null, vm.state.value.restartPromptFor)
    }

    @Test
    fun setMaster_true_keepsPerAnrToggles() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = false).also {
            it.setOn(Anr.ANR_02, true)
            it.setOn(Anr.ANR_06, true)
        }
        val vm = viewModelWith(fake)

        vm.setMaster(true)

        assertTrue(vm.state.value.master)
        assertTrue(fake.isOn(Anr.ANR_02))
        assertTrue(fake.isOn(Anr.ANR_06))
        assertTrue(vm.state.value.perAnr[Anr.ANR_02] ?: false)
        assertTrue(vm.state.value.perAnr[Anr.ANR_06] ?: false)
    }

    @Test
    fun setAnr_nonRequiresRestartAnr_dismissRestartPrompt_doesNotAffectFlag() = runTest(dispatcher) {
        val fake = FakeDemoFlags(master = true)
        val vm = viewModelWith(fake)

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

    // --- [ANR-04] 集中記録の暗号化トグル（鍵生成をどのスレッドで待つか） ---

    @Test
    fun setEncryptFocusRecords_anr04On_generatesKeyOnTheCallingThread() = runTest(dispatcher) {
        // ANR-04 の本体: 呼び出し元スレッド（本番ならメイン）が鍵生成の完了まで戻ってこない。
        val flags = FakeDemoFlags(master = true, perAnr = mutableMapOf(Anr.ANR_04 to true))
        DemoConfig.setFlagsForTest(flags)
        val vault = FakeSecureVault()
        val vm = viewModelWith(flags, vault = vault)
        val callerThreadName = Thread.currentThread().name

        vm.setEncryptFocusRecords(true)

        // 「戻ってきた時点でもう呼ばれている」＝同期的に待った証拠（coroutine を回す必要がない）。
        assertEquals(1, vault.callCount)
        assertEquals(callerThreadName, vault.callingThreadName)
    }

    @Test
    fun setEncryptFocusRecords_anr04Off_offloadsKeyGenerationOffTheCallingThread() = runTest(dispatcher) {
        // 正版: 呼び出し元は待たない。鍵生成はワーカー（Dispatchers.IO）で走る。
        val flags = FakeDemoFlags(master = true)
        DemoConfig.setFlagsForTest(flags)
        val vault = FakeSecureVault()
        val vm = viewModelWith(flags, vault = vault)
        val callerThreadName = Thread.currentThread().name

        vm.setEncryptFocusRecords(true)

        // 戻ってきた時点ではまだ呼ばれていない＝ブロックしていない。
        assertEquals(0, vault.callCount)
        // トグルの表示は鍵生成を待たずに即 ON（UI は固まらない）。
        assertTrue(vm.encryptFocusRecords.value)

        advanceUntilIdle()
        vm.joinPendingWork()

        assertEquals(1, vault.callCount)
        assertNotEquals(callerThreadName, vault.callingThreadName)
    }

    @Test
    fun setEncryptFocusRecords_off_doesNotGenerateKey() = runTest(dispatcher) {
        val flags = FakeDemoFlags(master = true, perAnr = mutableMapOf(Anr.ANR_04 to true))
        DemoConfig.setFlagsForTest(flags)
        val vault = FakeSecureVault()
        val vm = viewModelWith(flags, vault = vault)

        vm.setEncryptFocusRecords(false)
        advanceUntilIdle()

        assertEquals(0, vault.callCount)
        assertFalse(vm.encryptFocusRecords.value)
    }

    @Test
    fun setEncryptFocusRecords_alreadyOn_doesNotGenerateKeyAgain() = runTest(dispatcher) {
        // 鍵生成は OFF → ON の遷移でだけ走る（毎回作り直さない）。
        val flags = FakeDemoFlags(master = true, perAnr = mutableMapOf(Anr.ANR_04 to true))
        DemoConfig.setFlagsForTest(flags)
        val vault = FakeSecureVault()
        val vm = viewModelWith(flags, securitySettings = FakeSecuritySettings(enabled = true), vault = vault)
        advanceUntilIdle() // 永続値(true)の読み込みを反映させる
        assertTrue(vm.encryptFocusRecords.value)

        vm.setEncryptFocusRecords(true)

        assertEquals(0, vault.callCount)
    }

    @Test
    fun setEncryptFocusRecords_persistsTheToggle() = runTest(dispatcher) {
        val flags = FakeDemoFlags(master = false)
        DemoConfig.setFlagsForTest(flags)
        val settings = FakeSecuritySettings()
        val vm = viewModelWith(flags, securitySettings = settings)
        assertNull(settings.savedValue)

        vm.setEncryptFocusRecords(true)
        advanceUntilIdle()

        assertEquals(true, settings.savedValue)
    }

    @Test
    fun bindVault_andUnbindVault_delegateToTheVault() = runTest(dispatcher) {
        // 接続は設定画面の表示中だけ（Screen の DisposableEffect がこの2つを呼ぶ）。
        val vault = FakeSecureVault()
        val vm = viewModelWith(FakeDemoFlags(), vault = vault)

        vm.bindVault()
        vm.unbindVault()

        assertEquals(1, vault.bindCount)
        assertEquals(1, vault.unbindCount)
    }
}
