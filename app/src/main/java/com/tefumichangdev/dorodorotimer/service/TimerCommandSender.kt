package com.tefumichangdev.dorodorotimer.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** UI/VM から Service へのコマンド送出。VMはこのインターフェースだけに依存する。 */
interface TimerCommandSender {
    fun start()
    fun pause()
    fun reset()
}

/** Application Context を内包し、アクション付きIntentでServiceを叩く実装。 */
class AndroidTimerCommandSender(private val context: Context) : TimerCommandSender {

    override fun start() {
        // 初回はFGSへ昇格が必須なので startForegroundService。
        ContextCompat.startForegroundService(context, intentFor(TimerAction.START))
    }

    override fun pause() {
        // 既に起動済みServiceへのコマンドなので通常の startService で良い。
        context.startService(intentFor(TimerAction.PAUSE))
    }

    override fun reset() {
        context.startService(intentFor(TimerAction.RESET))
    }

    private fun intentFor(action: String): Intent =
        Intent(context, TimerForegroundService::class.java).setAction(action)
}
