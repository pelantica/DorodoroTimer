package com.pelantica.dorodorotimer.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 雨音（環境音）の再生コントロール。UI/VM はこの IF だけに依存する。 */
interface AmbientSoundController {
    val isPlaying: StateFlow<Boolean>
    fun toggle()
    fun play()
    fun stop()
}

/** Application Context を内包し、AmbientSoundService をアクション付き Intent で操作する。 */
class AndroidAmbientSoundController(private val context: Context) : AmbientSoundController {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    override fun toggle() {
        if (_isPlaying.value) stop() else play()
    }

    override fun play() {
        _isPlaying.value = true
        // mediaPlayback FGS への昇格が要るので startForegroundService。
        ContextCompat.startForegroundService(context, intentFor(AmbientSoundAction.PLAY))
    }

    override fun stop() {
        _isPlaying.value = false
        context.startService(intentFor(AmbientSoundAction.STOP))
    }

    private fun intentFor(action: String): Intent =
        Intent(context, AmbientSoundService::class.java).setAction(action)
}
