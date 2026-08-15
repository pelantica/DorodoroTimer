package com.pelantica.dorodorotimer.app

import android.app.Application
import com.pelantica.dorodorotimer.app.startup.StartupGate
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
        // [ANR-02] 「SDK風」初期化6つを、どのスレッドで走らせるか。
        //  顔ぶれ・順番・作業量は StartupGate.runAll にだけ書いてあり、下の2経路はどちらも
        //  それを呼ぶ。つまり ANR するかしないかの差分は、この呼び分けの1行だけになる。
        if (DemoConfig.isOn(Anr.ANR_02)) {
            // [ANR-02] メイン（onCreate のスレッド）で同期実行する。
            //  呼び出し側から見ると1行で重さが見えないが、中では6つ分を丸ごと払い、合計で
            //  起動ANRのしきい値（入力ディスパッチ5秒）を超える。単独犯ではなく初期化の
            //  総量が原因＝「千のかすり傷」。実機校正の記録は StartupGate の KDoc。
            //  処方: 下の else 側。ほかに各 init() を Koin lazyModule 化して先送りする手もある。
            StartupGate.runOnMainThread(this)
        } else {
            // 正版: 同じ6つをワーカースレッドへ「予約」して即返す。仕事の総量は1ミリも
            //  減っていない（サボりではない）＝処方「onCreateは予約だけ。仕事をしない」の実物。
            //  ビルド種別で括らずリリースでも走らせるのは、このアプリが一旦は教材だから。
            //  TODO(製品化): 6つは教材用の「SDKもどき」＝純粋な重りなので、製品として出すときは
            //   初期化すべき本物のSDKに置き換える（そのまま残すと毎起動9秒ぶんのCPUと
            //   数千回のfsyncを焼く）。本物のSDKを抱えるアプリでは、この else 側が本来の実装。
            StartupGate.runOnWorkerThread(this)
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
