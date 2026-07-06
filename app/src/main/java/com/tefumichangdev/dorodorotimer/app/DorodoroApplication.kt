package com.tefumichangdev.dorodorotimer.app

import android.app.Application
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig
import com.tefumichangdev.dorodorotimer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DorodoroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DemoConfig.init(this)
        if (DemoConfig.isOn(Anr.ANR_02)) {
            // [ANR-02] 起動時にメインで重い同期初期化を eager 実行 → Application.onCreate ANR。
            //  処方: startKoin を lazyModule 化し、重い初期化を必要時まで先送りする。
            StartupInitializer.runHeavyEagerInit()
        }
        startKoin {
            androidContext(this@DorodoroApplication)
            modules(appModule)
        }
    }
}
