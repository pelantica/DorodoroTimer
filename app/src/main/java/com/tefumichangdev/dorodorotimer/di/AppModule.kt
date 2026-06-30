package com.tefumichangdev.dorodorotimer.di

import androidx.room.Room
import com.tefumichangdev.dorodorotimer.core.debug.DemoFlags
import com.tefumichangdev.dorodorotimer.core.debug.SharedPrefsDemoFlags
import com.tefumichangdev.dorodorotimer.data.local.datastore.DataStorePomodoroSettingsRepository
import com.tefumichangdev.dorodorotimer.data.local.datastore.DataStoreTimerStateRepository
import com.tefumichangdev.dorodorotimer.data.local.datastore.pomodoroDataStore
import com.tefumichangdev.dorodorotimer.data.local.room.AppDatabase
import com.tefumichangdev.dorodorotimer.domain.repository.PomodoroSettingsRepository
import com.tefumichangdev.dorodorotimer.domain.repository.TimerStateRepository
import com.tefumichangdev.dorodorotimer.feature.settings.SettingsViewModel
import com.tefumichangdev.dorodorotimer.feature.timer.TimerViewModel
import com.tefumichangdev.dorodorotimer.service.AmbientSoundController
import com.tefumichangdev.dorodorotimer.service.AndroidAmbientSoundController
import com.tefumichangdev.dorodorotimer.service.AndroidTimerScheduler
import com.tefumichangdev.dorodorotimer.service.TimerScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<DemoFlags> { SharedPrefsDemoFlags(androidContext()) }

    single<PomodoroSettingsRepository> {
        DataStorePomodoroSettingsRepository(androidContext().pomodoroDataStore)
    }

    single<TimerStateRepository> {
        DataStoreTimerStateRepository(androidContext().pomodoroDataStore)
    }

    single<TimerScheduler> { AndroidTimerScheduler(androidContext()) }

    single<AmbientSoundController> { AndroidAmbientSoundController(androidContext()) }

    // Room（「スレッドを管理してくれる」側）。骨格では生成のみ。
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "dorodoro.db").build()
    }
    single { get<AppDatabase>().focusSessionDao() }

    viewModel { TimerViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
}

// TODO(ANR-02 / ANR-03 / ANR-07): 起動時の初期化集中・ClassLoader 起因のANRの「処方」をここで実演する。
//  - demoMode ON: 重い依存を eager な module { single { Heavy() } } で起動時生成 → ②③⑦ を誘発
//  - demoMode OFF: lazyModule { } ＋ 遅延 single に置き換え → 必要時まで生成・クラスロードを先送り
//  例:
//    val lazyAppModule = lazyModule { single { /* 重い依存 */ } }
//    // DorodoroApplication: startKoin { lazyModules(lazyAppModule) }
//
// TODO(ANR-01): SQLDelight（「スレッドを管理してくれない」側）のドライバ／DB を Koin に追加し、
//  Room との対比で「同じI/Oでも呼んだスレッドで同期実行される」を見せる。
//  例:
//    single<SqlDriver> { AndroidSqliteDriver(StatsDatabase.Schema, androidContext(), "stats.db") }
//    single { StatsDatabase(get()) }
