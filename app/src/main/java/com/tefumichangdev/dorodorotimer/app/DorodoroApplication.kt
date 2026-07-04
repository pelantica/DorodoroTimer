package com.tefumichangdev.dorodorotimer.app

import android.app.Application
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig
import com.tefumichangdev.dorodorotimer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DorodoroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DemoConfig.init(this)
        // TODO(ANR-02): demoMode ON では、ここで重い初期化を eager に走らせて
        //  Application.onCreate ANR を再現する（分析SDK風 init / Room 同期マイグレーション /
        //  Koin 定義の eager 生成など）。OFF では lazyModule で先送りする。
        startKoin {
            androidContext(this@DorodoroApplication)
            modules(appModule)
        }
    }
}
