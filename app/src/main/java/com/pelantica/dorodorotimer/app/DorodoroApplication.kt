package com.pelantica.dorodorotimer.app

import android.app.Application
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.core.debug.StrictModeBannerSettings
import com.pelantica.dorodorotimer.core.debug.StrictModeInstaller
import com.pelantica.dorodorotimer.di.appModule
import com.pelantica.dorodorotimer.service.work.AnrLogUploadScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DorodoroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // demoMode とは無関係に、デバッグビルドでは常にメインスレッドのI/Oを見張る。
        // onCreate の先頭に置くのは、直後の DemoConfig.init（SharedPreferences の読み込み）
        // も観測対象に含めるため。SharedPreferences の実際の読み込みは別スレッドで走るが、
        // main が待つ間に AOSP 側が明示的に違反を立てるので、これはちゃんと検出される
        // （SharedPreferencesImpl#awaitLoadedLocked）。自分のコードが最初の違反になる。
        StrictModeInstaller.install()
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
        if (DemoConfig.isOn(Anr.ANR_05)) {
            // [ANR-05] demoMode 時、ANRログ送信を模して Work を enqueue する。
            //  冷えたプロセスに WorkManager がジョブを投げると新プロセスが立ち上がり、
            //  ANR-02 の重い onCreate が起動枠内で走ることで起動 ANR になる（連結シナリオ）。
            //  ANR-02 トグルも ON にして、アプリを BG に落とした後に発火させること。
            AnrLogUploadScheduler.enqueue(this)
        }
        // onCreate の末尾に置く: 同じ prefs を DemoConfig.isOn が既にロード済みなので
        // ここで読んでも違反が出ず、起動時違反のトレースの帰属も変わらない。
        StrictModeBannerSettings.init(this)
    }
}
