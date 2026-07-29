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
        flags.setMaster(on)
        _state.value = flags.snapshot()
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
