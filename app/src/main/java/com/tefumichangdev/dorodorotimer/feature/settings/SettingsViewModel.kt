package com.tefumichangdev.dorodorotimer.feature.settings

import androidx.lifecycle.ViewModel
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoFlags
import com.tefumichangdev.dorodorotimer.core.debug.DemoFlagsState
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
        _state.value = flags.snapshot()
    }
}
