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
        flags.setOn(anr, on)
        _state.value = flags.snapshot().copy(
            restartPromptFor = if (anr.requiresRestart) anr else null,
        )
    }

    /** 再起動ダイアログを閉じる（[AppRestarter] の呼び出しは Context を持つ Screen 側の責務）。 */
    fun dismissRestartPrompt() {
        _state.value = _state.value.copy(restartPromptFor = null)
    }
}
