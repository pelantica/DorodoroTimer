package com.tefumichangdev.dorodorotimer.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.tefumichangdev.dorodorotimer.data.local.datastore.DataStorePomodoroSettingsRepository
import com.tefumichangdev.dorodorotimer.data.local.datastore.DataStoreTimerStateRepository
import com.tefumichangdev.dorodorotimer.data.local.room.AppDatabase
import com.tefumichangdev.dorodorotimer.domain.repository.PomodoroSettingsRepository
import com.tefumichangdev.dorodorotimer.domain.repository.TimerStateRepository
import com.tefumichangdev.dorodorotimer.feature.timer.TimerViewModel
import com.tefumichangdev.dorodorotimer.service.AmbientSoundController
import com.tefumichangdev.dorodorotimer.service.AndroidAmbientSoundController
import com.tefumichangdev.dorodorotimer.service.AndroidTimerScheduler
import com.tefumichangdev.dorodorotimer.service.TimerScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // アプリ単一の Preferences DataStore。生成は DI が所有し（@Singleton 相当）、各 Repository は
    // get() で受け取る。設定とタイマー状態は同一ストアをキー分割で共有する（ファイルは1つ）。
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create {
            androidContext().preferencesDataStoreFile("pomodoro_settings")
        }
    }

    single<PomodoroSettingsRepository> { DataStorePomodoroSettingsRepository(get()) }

    single<TimerStateRepository> { DataStoreTimerStateRepository(get()) }

    single<TimerScheduler> { AndroidTimerScheduler(androidContext()) }

    single<AmbientSoundController> { AndroidAmbientSoundController(androidContext()) }

    // Room（「スレッドを管理してくれる」側）。骨格では生成のみ。
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "dorodoro.db").build()
    }
    single { get<AppDatabase>().focusSessionDao() }

    viewModel { TimerViewModel(get(), get(), get(), get()) }
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
