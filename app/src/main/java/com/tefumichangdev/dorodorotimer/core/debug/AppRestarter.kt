package com.tefumichangdev.dorodorotimer.core.debug

import android.content.Context
import android.content.Intent

/**
 * demoMode 設定画面から「アプリを再起動する」ためのユーティリティ。
 *
 * [Anr.requiresRestart] == true なトグル（Koin `single` のDI差し替え・`Application.onCreate`
 * 内の分岐）は、プロセスを実際に作り直さないと変更が反映されない。
 * `Activity.recreate()` は同一プロセス内で Activity を作り直すだけで Koin の singleton や
 * Application.onCreate はそのままのため使えない。
 *
 * 自身のランチャー Intent を `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` で
 * startActivity してから [Runtime.exit] でプロセスを終了させることで、
 * OS に新しいプロセス／Application インスタンスを作らせる。
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
