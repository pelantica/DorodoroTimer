package com.tefumichangdev.dorodorotimer.di

import androidx.room.Room
import com.tefumichangdev.dorodorotimer.data.local.room.AppDatabase
import com.tefumichangdev.dorodorotimer.domain.model.PomodoroPreset
import com.tefumichangdev.dorodorotimer.feature.timer.TimerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { PomodoroPreset.Default }

    // Room（「スレッドを管理してくれる」側）。骨格では生成のみ。
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "dorodoro.db").build()
    }
    single { get<AppDatabase>().focusSessionDao() }

    viewModel { TimerViewModel(get()) }
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
