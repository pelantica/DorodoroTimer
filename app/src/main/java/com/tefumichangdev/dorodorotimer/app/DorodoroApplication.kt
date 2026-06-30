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
        if (DemoConfig.isOn(Anr.ANR_07)) {
            // [ANR-07] 起動時に多数クラスを eager 実体化＝ClassLoader/初期化が起動枠に集中。
            //  処方: Koin lazyModule で遅延し、起動時に触るクラス数を減らす。
            //  ※ ANR-02（少数クラスの「重いCPU計算」）とは別問題:
            //     ANR-07 は「多数クラスのロード・初期化コストの積み重なり」が原因。
            EagerGraph.forceLoadAll()
        }
        startKoin {
            androidContext(this@DorodoroApplication)
            modules(appModule)
        }
    }
}
