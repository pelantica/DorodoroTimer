package com.tefumichangdev.dorodorotimer.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * タイマー継続用フォアグラウンドサービス（骨格では器のみ）。
 *
 * TODO(ANR-FGS): startForegroundService() で起動された場合、5 秒以内に startForeground() を
 *  呼ぶ責務がある。demoMode ON では「先に重い初期化をしてから startForeground を遅らせる」ことで
 *  service 系 ANR（締切 5 秒）を再現する。OFF では即 startForeground する。
 */
class TimerForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
