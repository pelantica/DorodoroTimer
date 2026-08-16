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
import com.pelantica.dorodorotimer.data.local.datastore.DataStoreSecuritySettingsRepository
import com.pelantica.dorodorotimer.data.local.datastore.DataStoreTimerStateRepository
import com.pelantica.dorodorotimer.data.local.room.AppDatabase
import com.pelantica.dorodorotimer.data.local.room.RoomFocusSessionRecorder
import com.pelantica.dorodorotimer.data.local.stats.BlockingStatsRepository
import com.pelantica.dorodorotimer.data.local.stats.OffloadedStatsRepository
import com.pelantica.dorodorotimer.data.local.stats.RawSqliteStatsHelper
import com.pelantica.dorodorotimer.domain.repository.FocusSessionRecorder
import com.pelantica.dorodorotimer.domain.repository.PomodoroPresetRepository
import com.pelantica.dorodorotimer.domain.repository.SecuritySettingsRepository
import com.pelantica.dorodorotimer.domain.repository.StatsRepository
import com.pelantica.dorodorotimer.domain.repository.TimerStateRepository
import com.pelantica.dorodorotimer.feature.settings.SettingsViewModel
import com.pelantica.dorodorotimer.feature.stats.StatsViewModel
import com.pelantica.dorodorotimer.feature.timer.TimerViewModel
import com.pelantica.dorodorotimer.service.AmbientSoundController
import com.pelantica.dorodorotimer.service.AndroidAmbientSoundController
import com.pelantica.dorodorotimer.service.AndroidTimerScheduler
import com.pelantica.dorodorotimer.service.TimerScheduler
import com.pelantica.dorodorotimer.vendor.securevault.SecureVault
import com.pelantica.dorodorotimer.vendor.securevault.SecureVaultClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<DemoFlags> { DemoConfig.current() }

    // アプリ単一の Preferences DataStore。生成は DI が所有し（@Singleton 相当）、各 Repository は
    // get() で受け取る。設定とタイマー状態は同一ストアをキー分割で共有する（ファイルは1つ）。
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create {
            androidContext().preferencesDataStoreFile("pomodoro_settings")
        }
    }

    single<PomodoroPresetRepository> { DataStorePomodoroPresetRepository(get()) }

    single<TimerStateRepository> { DataStoreTimerStateRepository(get()) }

    single<TimerScheduler> { AndroidTimerScheduler(androidContext()) }

    single<AmbientSoundController> { AndroidAmbientSoundController(androidContext()) }

    // Room（「スレッドを管理してくれる」側）。骨格では生成のみ。
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "dorodoro.db")
            .build()
    }
    single { get<AppDatabase>().focusSessionDao() }

    // 完了セッションの記録。ANR-01 の差し替え対象（StatsRepository）とは独立に、
    // 常に安全な Room 経路だけを配線する（理由は FocusSessionRecorder の KDoc）。
    single<FocusSessionRecorder> { RoomFocusSessionRecorder(get()) }

    // [ANR-01] 生SQLite（「守ってくれない」側）ヘルパー。SQLDelight が AGP9 未対応のため生SQLiteで代替。
    single { RawSqliteStatsHelper(androidContext()) }

    // [ANR-01] demoMode ON → BlockingStatsRepository（生SQLite・呼んだスレッドで同期実行→ANR）
    //          demoMode OFF → OffloadedStatsRepository（Room suspend DAO ＋ withContext(IO)→安全）
    //  原因が「実装まるごとの性質」なので DI で実装ごと差し替える（規約のDI-swapパターン）。
    //  seedDemoData には master トグル（DemoConfig.enabled）を**読む関数**を渡す。ANR-01 個別トグルが
    //  OFF でも master が ON（＝demoMode中）なら Offloaded 側にも同じデモデータを入れて、
    //  「ANRするかしないか」だけを公平に対比できるようにする。master が OFF（リリース）のときは
    //  絶対に false になり、架空データを作らない。
    //  値ではなく関数で渡すのは、この single が生成後キャッシュされるため（理由は
    //  OffloadedStatsRepository の KDoc）。どちらの実装を注入するかは生成時に固定でよい
    //  ＝ ANR_01 が requiresRestart = true であることと対応する。
    single<StatsRepository> {
        if (DemoConfig.isOn(Anr.ANR_01)) {
            BlockingStatsRepository(get())
        } else {
            OffloadedStatsRepository(get(), seedDemoData = { DemoConfig.enabled })
        }
    }

    single<SecuritySettingsRepository> { DataStoreSecuritySettingsRepository(get()) }

    // [ANR-04] 「外部SDK風」の鍵庫クライアント。接続の張り直しを避けるため single。
    //  実際に bind/unbind するのは設定画面が見えている間だけ（SettingsScreen の DisposableEffect）。
    //  ANR-04 の分岐は「どのスレッドで待つか」という局所操作なので、DI で実装を差し替えるのではなく
    //  呼び出し箇所（SettingsViewModel）のローカル if にしている（規約の「原因の粒度」に対応）。
    single<SecureVault> { SecureVaultClient(androidContext()) }

    viewModel { TimerViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    // realRepo は実データ専用の読み口。ANR-01 の差し替え対象（demoRepo）とは独立に、
    // 常に安全な Room 経路＝シードを投入しない OffloadedStatsRepository を渡す。
    // demoRepo と realRepo は同じ型なので、取り違え防止に名前付き引数で渡す。
    viewModel {
        StatsViewModel(
            demoRepo = get(),
            realRepo = OffloadedStatsRepository(get()),
            isDemoMode = { DemoConfig.enabled },
        )
    }
}

// TODO(ANR-02 / ANR-03 / ANR-07): 起動時の初期化集中・ClassLoader 起因のANRの「処方」をここで実演する。
//  - demoMode ON: 重い依存を eager な module { single { Heavy() } } で起動時生成 → ②③⑦ を誘発
//  - demoMode OFF: lazyModule { } ＋ 遅延 single に置き換え → 必要時まで生成・クラスロードを先送り
//  例:
//    val lazyAppModule = lazyModule { single { /* 重い依存 */ } }
//    // DorodoroApplication: startKoin { lazyModules(lazyAppModule) }
