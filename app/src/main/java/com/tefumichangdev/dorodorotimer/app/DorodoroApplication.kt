package com.tefumichangdev.dorodorotimer.app

import android.app.Application
import android.util.Log
import com.tefumichangdev.dorodorotimer.app.startup.AnalyticsInitializer
import com.tefumichangdev.dorodorotimer.app.startup.CrashReportingInitializer
import com.tefumichangdev.dorodorotimer.app.startup.FeatureFlagInitializer
import com.tefumichangdev.dorodorotimer.app.startup.ImageLoaderInitializer
import com.tefumichangdev.dorodorotimer.app.startup.PerformanceMonitorInitializer
import com.tefumichangdev.dorodorotimer.app.startup.RemoteConfigInitializer
import com.tefumichangdev.dorodorotimer.core.debug.Anr
import com.tefumichangdev.dorodorotimer.core.debug.DemoConfig
import com.tefumichangdev.dorodorotimer.di.appModule
import com.tefumichangdev.dorodorotimer.service.work.AnrLogUploadScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DorodoroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DemoConfig.init(this)
        if (DemoConfig.isOn(Anr.ANR_02)) {
            // [ANR-02] 「SDK風」初期化を Application.onCreate から同期的に、1行ずつ列挙して呼ぶ。
            //  1行ずつは無害に見える（呼び出し側から重さが見えない）が、合計で起動ANRのしきい値
            //  (入力ディスパッチ5秒)を超える「千のかすり傷」＝単独犯ではなく初期化の総量が原因。
            //  処方: 各 init() を Koin lazyModule 化する、あるいは必要時まで先送りする。
            //
            //  実機校正の記録（エミュ API 36 (sdk_gphone16k_arm64) / 2026-07-30、`adb shell am start -W`）:
            //   - ON:  TotalTime 9080〜9934ms（onCreate内の同期初期化だけで合計6.6〜8.0秒、
            //          `Log.d` した各SDK風初期化の内訳は各ファイルのKDoc参照。1つが突出せず分散）。
            //   - OFF: TotalTime 1497〜2222ms（このブロックが丸ごとスキップされるため無関係）。
            //   - 差分: 約7300〜8400ms。目標の5000ms超を安定して達成。
            //   - 実機ANR確認: 起動中に `adb shell input keyevent KEYCODE_DPAD_CENTER` を送ると
            //     `ActivityManager: ANR in com.tefumichangdev.dorodorotimer ... Reason: Input
            //     dispatching timed out (Application does not have a focused window).` が発生
            //     （logcat実測）。一方 `adb shell input tap` の単純タップは Android 12+ の
            //     スプラッシュ用 `ActivityRecordInputSink`（NO_INPUT_CHANNEL）に吸収され ANR を
            //     誘発しない場合がある点に注意（実機デモ台本ではキー入力を使う想定にする）。
            val startupStartMs = System.currentTimeMillis()
            AnalyticsInitializer.init(this)
            CrashReportingInitializer.init(this)
            RemoteConfigInitializer.init(this)
            FeatureFlagInitializer.init(this)
            ImageLoaderInitializer.init(this)
            PerformanceMonitorInitializer.init(this)
            Log.d(
                "StartupInitializer",
                "total sync init time: ${System.currentTimeMillis() - startupStartMs}ms"
            )
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
    }
}
