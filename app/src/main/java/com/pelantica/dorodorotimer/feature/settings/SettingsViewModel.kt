package com.pelantica.dorodorotimer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.core.debug.DemoFlags
import com.pelantica.dorodorotimer.core.debug.DemoFlagsState
import com.pelantica.dorodorotimer.core.report.CrashReportBreadcrumbs
import com.pelantica.dorodorotimer.domain.repository.SecuritySettingsRepository
import com.pelantica.dorodorotimer.vendor.securevault.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val flags: DemoFlags,
    private val securitySettings: SecuritySettingsRepository,
    private val vault: SecureVault,
) : ViewModel() {

    private val _state = MutableStateFlow(flags.snapshot())
    val state: StateFlow<DemoFlagsState> = _state.asStateFlow()

    private val _encryptFocusRecords = MutableStateFlow(false)
    /** 「集中記録を暗号化」トグルの表示状態。永続値の読み込み前は既定の false。 */
    val encryptFocusRecords: StateFlow<Boolean> = _encryptFocusRecords.asStateFlow()

    init {
        viewModelScope.launch {
            _encryptFocusRecords.value = securitySettings.isEncryptFocusRecordsEnabled()
        }
    }

    /**
     * 鍵庫（別プロセス）への接続を開始する。設定画面が表示されている間だけ繋ぐ
     * （呼び出しは [SettingsScreen] の DisposableEffect）。
     */
    fun bindVault() = vault.bind()

    /** 鍵庫への接続を切る。 */
    fun unbindVault() = vault.unbind()

    /**
     * 「集中記録を暗号化」トグルの操作。OFF → ON になったときだけ鍵を生成する。
     *
     * トグルの状態は鍵生成の結果を待たずに即座に反映・保存する（正版の UI は固まらない）。
     * ANR-04 が ON のときだけ、鍵生成の待ち方がメインスレッドに変わる。
     */
    fun setEncryptFocusRecords(enabled: Boolean) {
        val wasEnabled = _encryptFocusRecords.value
        _encryptFocusRecords.value = enabled
        viewModelScope.launch { securitySettings.setEncryptFocusRecordsEnabled(enabled) }
        if (!enabled || wasEnabled) return

        if (DemoConfig.isOn(Anr.ANR_04)) {
            // [ANR-04] 鍵生成をメインスレッドで同期待ちする。Binder の向こう（セキュアHWの代役）が
            //  返すまで main は transact で止まる＝waiting(binder)。呼び出しはこの1行で、
            //  重い処理は自分のコードに1行も無い（トレースにも自分のコードはここしか出ない）。
            //  処方は下の else 側＝withContext(IO)。相手を速くする手段はアプリ側に無いので、
            //  「速くする」のではなく「待ち方を変える」しかない。詳細は SecureVaultClient の KDoc。
            vault.generateKeyBlocking()
        } else {
            // 正版: 待つのはワーカー。メインは即座に描き続ける（トグルはもう ON になっている）。
            viewModelScope.launch {
                withContext(Dispatchers.IO) { vault.generateKeyBlocking() }
            }
        }
    }

    /**
     * マスタートグルを切り替える。
     *
     * OFF にするときは、ON になっている個別 ANR トグルもまとめて OFF にする
     * （マスター OFF 中は個別 Switch が disabled になり、内部だけ ON が残ると
     * 「次にマスターを ON にした瞬間に思わぬ事例が有効になる」ため）。
     * 確認ダイアログは出さない代わりに、設定画面のマスターカードに常設の注記を置いている。
     *
     * [DemoFlagsState.restartPromptFor] は立てない。再起動プロンプトは個別トグル操作
     * （[setAnr]）からのみ立つもので、マスター OFF は「まとめて無効化する」操作だから。
     */
    fun setMaster(on: Boolean) {
        if (!on) {
            Anr.entries.filter { flags.isOn(it) }.forEach { flags.setOn(it, false) }
        }
        flags.setMaster(on)
        _state.value = flags.snapshot()
        // 一括クリアでキーが実状態からズレるため、Crashlytics のカスタムキーを貼り直す。
        // 書き込みは Crashlytics 内部のワーカーに積まれるだけでメインスレッド I/O にはならない。
        CrashReportBreadcrumbs.setDemoFlags(_state.value)
    }

    fun setAnr(anr: Anr, on: Boolean) {
        val previousValue = flags.isOn(anr)
        flags.setOn(anr, on)
        _state.value = flags.snapshot().copy(
            restartPromptFor = if (anr.requiresRestart) anr else null,
            restartPromptPreviousValue = previousValue,
        )
    }

    /**
     * 再起動確認ダイアログを「キャンセル」で閉じる（あとで／ダイアログ外タップ／戻る操作）。
     * [Anr.requiresRestart] のトグルは再起動するまで実際の挙動に反映されないため、
     * 変更前の値へ書き戻して「トグルの表示＝実際に効いている状態」を保つ。
     */
    fun dismissRestartPrompt() {
        val anr = _state.value.restartPromptFor ?: return
        flags.setOn(anr, _state.value.restartPromptPreviousValue)
        _state.value = flags.snapshot().copy(restartPromptFor = null)
    }

    /**
     * 再起動確認ダイアログを「再起動する」で閉じる。フラグは変更後の値のまま維持し、
     * プロンプト状態だけをクリアする（[AppRestarter] の呼び出しは Context を持つ Screen 側の責務）。
     */
    fun confirmRestartPrompt() {
        _state.value = _state.value.copy(restartPromptFor = null)
    }
}
