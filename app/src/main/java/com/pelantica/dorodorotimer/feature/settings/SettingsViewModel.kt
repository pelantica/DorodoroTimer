package com.pelantica.dorodorotimer.feature.settings

import androidx.lifecycle.ViewModel
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoFlags
import com.pelantica.dorodorotimer.core.debug.DemoFlagsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val flags: DemoFlags) : ViewModel() {

    private val _state = MutableStateFlow(flags.snapshot())
    val state: StateFlow<DemoFlagsState> = _state.asStateFlow()

    fun setMaster(on: Boolean) {
        if (on) {
            flags.setMaster(true)
            _state.value = flags.snapshot()
            return
        }

        // マスターOFF: ON中の個別トグルを一括クリアする（表示上ONのままDIだけ止まる、という
        // 見た目と実挙動の食い違いを防ぐため）。クリア対象を先に記録してから書き込む。
        val clearedAnrs = Anr.entries.filter { flags.isOn(it) }
        clearedAnrs.forEach { flags.setOn(it, false) }
        flags.setMaster(false)

        val requiresRestartAnrs = clearedAnrs.filter { it.requiresRestart }
        val restartPrompt = requiresRestartAnrs.takeIf { it.isNotEmpty() }
        _state.value = flags.snapshot().copy(
            restartPromptForMasterOff = restartPrompt,
            clearedAnrsOnMasterOff = if (restartPrompt != null) clearedAnrs else emptyList(),
        )
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

    /**
     * マスターOFF起因の再起動確認ダイアログを「キャンセル」で閉じる（あとで／ダイアログ外タップ／戻る操作）。
     * 一括クリアした個別トグルとマスターをすべて ON に書き戻し、[dismissRestartPrompt] と同様に
     * 「トグルの表示＝実際に効いている状態」を保つ。
     */
    fun dismissMasterOffRestartPrompt() {
        _state.value.restartPromptForMasterOff ?: return
        val clearedAnrs = _state.value.clearedAnrsOnMasterOff
        clearedAnrs.forEach { flags.setOn(it, true) }
        flags.setMaster(true)
        _state.value = flags.snapshot().copy(
            restartPromptForMasterOff = null,
            clearedAnrsOnMasterOff = emptyList(),
        )
    }

    /**
     * マスターOFF起因の再起動確認ダイアログを「再起動する」で閉じる。フラグ（マスターOFF・
     * 個別トグル一括クリア済み）は変更後のまま維持し、プロンプト状態だけをクリアする
     * （[AppRestarter] の呼び出しは Context を持つ Screen 側の責務）。
     */
    fun confirmMasterOffRestartPrompt() {
        _state.value = _state.value.copy(
            restartPromptForMasterOff = null,
            clearedAnrsOnMasterOff = emptyList(),
        )
    }
}
