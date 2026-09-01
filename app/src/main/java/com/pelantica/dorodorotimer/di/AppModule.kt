package com.pelantica.dorodorotimer.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.core.debug.DemoFlags
import com.pelantica.dorodorotimer.data.local.datastore.DataStorePomodoroPresetRepository
import com.pelantica.dorodorotimer.data.local.datastore.DataStoreTimerStateRepository
import com.pelantica.dorodorotimer.data.local.room.AppDatabase
import com.pelantica.dorodorotimer.data.local.room.RoomFocusSessionRecorder
import com.pelantica.dorodorotimer.data.local.stats.BlockingStatsRepository
import com.pelantica.dorodorotimer.data.local.stats.OffloadedStatsRepository
import com.pelantica.dorodorotimer.data.local.stats.RawSqliteStatsHelper
import com.pelantica.dorodorotimer.domain.repository.FocusSessionRecorder
import com.pelantica.dorodorotimer.domain.repository.PomodoroPresetRepository
import com.pelantica.dorodorotimer.domain.repository.StatsRepository
import com.pelantica.dorodorotimer.domain.repository.TimerStateRepository
import com.pelantica.dorodorotimer.feature.settings.SettingsViewModel
import com.pelantica.dorodorotimer.feature.stats.StatsViewModel
import com.pelantica.dorodorotimer.feature.timer.TimerViewModel
import com.pelantica.dorodorotimer.service.AmbientSoundController
import com.pelantica.dorodorotimer.service.AndroidAmbientSoundController
import com.pelantica.dorodorotimer.service.AndroidTimerScheduler
import com.pelantica.dorodorotimer.service.TimerScheduler
import com.pelantica.dorodorotimer.vendor.securevault.CachingSecureVaultKeyProvider
import com.pelantica.dorodorotimer.vendor.securevault.SecureVaultKeyProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<DemoFlags> { DemoConfig.current() }

    // アプリ単一の Preferences DataStore。設定とタイマー状態は同一ストアをキー分割で共有する。
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create {
            androidContext().preferencesDataStoreFile("pomodoro_settings")
        }
    }

    single<PomodoroPresetRepository> { DataStorePomodoroPresetRepository(get()) }

    single<TimerStateRepository> { DataStoreTimerStateRepository(get()) }

    single<TimerScheduler> { AndroidTimerScheduler(androidContext()) }

    single<AmbientSoundController> { AndroidAmbientSoundController(androidContext()) }

    // Room（「スレッドを管理してくれる」側）。
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "dorodoro.db")
            .build()
    }
    single { get<AppDatabase>().focusSessionDao() }

    // 完了セッションの記録。ANR-01 の差し替え対象（StatsRepository）とは独立に、
    // 常に安全な Room 経路だけを配線する。
    single<FocusSessionRecorder> { RoomFocusSessionRecorder(get()) }

    // [ANR-01] 生SQLite（「守ってくれない」側）ヘルパー。
    single { RawSqliteStatsHelper(androidContext()) }

    // [ANR-04][正版] 鍵庫の鍵を遅延・背面・キャッシュで供給するクライアント。
    // ANR-04 が onCreate でメイン同期待ちするのと対照的に、実際に鍵が要る場面で初めて呼ばれる。
    single<SecureVaultKeyProvider> { CachingSecureVaultKeyProvider(androidContext()) }

    // [ANR-01] demoMode ON → BlockingStatsRepository（生SQLite・呼んだスレッドで同期実行→ANR）
    //          demoMode OFF → OffloadedStatsRepository（Room suspend DAO が IO へ逃がす→安全）
    //  seedDemoData には master トグル（DemoConfig.enabled）を読む関数を渡す: ANR-01 個別トグルが
    //  OFF でも demoMode 中なら Offloaded 側にも同じデモデータが入り、「ANRするかしないか」だけを
    //  公平に対比できる。master が OFF（リリース）のときは絶対に false＝架空データを作らない。
    single<StatsRepository> {
        if (DemoConfig.isOn(Anr.ANR_01)) {
            BlockingStatsRepository(get())
        } else {
            OffloadedStatsRepository(get(), seedDemoData = { DemoConfig.enabled })
        }
    }

    viewModel { TimerViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    // realRepo は実データ専用の読み口。ANR-01 の差し替え対象（demoRepo）とは独立に、
    // 常に安全な Room 経路＝シードを投入しない OffloadedStatsRepository を渡す。
    viewModel {
        StatsViewModel(
            demoRepo = get(),
            realRepo = OffloadedStatsRepository(get()),
            isDemoMode = { DemoConfig.enabled },
            vaultKey = get(),
        )
    }
}

// TODO(ANR-07): ClassLoader 起因の ANR とその処方（Koin lazyModule で遅延）をここで実演する予定。
