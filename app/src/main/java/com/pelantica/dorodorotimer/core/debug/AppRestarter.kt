package com.pelantica.dorodorotimer.core.debug

import android.content.Context
import android.content.Intent

/**
 * demoMode 設定画面からアプリを再起動するユーティリティ（[Anr.requiresRestart] == true のトグル用）。
 *
 * `Activity.recreate()` は同一プロセス内で Activity を作り直すだけで Koin の singleton や
 * Application.onCreate には効かないため、ランチャー Intent を投げてからプロセスを終了し、
 * OS に新しいプロセスを作らせる。
 */
object AppRestarter {

    fun restart(context: Context) {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
        Runtime.getRuntime().exit(0)
    }
}
