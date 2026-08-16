package com.pelantica.dorodorotimer.app

import android.app.Application
import com.pelantica.dorodorotimer.app.startup.StartupGate
import com.pelantica.dorodorotimer.app.startup.StartupOrigin
import com.pelantica.dorodorotimer.app.startup.UnsentReportIndexInitializer
import com.pelantica.dorodorotimer.core.debug.Anr
import com.pelantica.dorodorotimer.core.debug.DemoConfig
import com.pelantica.dorodorotimer.core.report.CrashReportBreadcrumbs
import com.pelantica.dorodorotimer.core.debug.StrictModeBannerSettings
import com.pelantica.dorodorotimer.core.debug.StrictModeInstaller
import com.pelantica.dorodorotimer.data.local.stats.StatsStore
import com.pelantica.dorodorotimer.di.appModule
import com.pelantica.dorodorotimer.service.work.AnrLogUploadScheduler
import kotlin.concurrent.thread
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
        // demoMode のトグル状態を Crashlytics のカスタムキーに載せる。ANR レポートを開いたとき
        // 「どのトグルが ON のセッションだったか」がスタックを読む前に分かる。
        // ANR-02 の分岐より前に置く: 直後の起動フリーズ中のセッションにもキーが乗るように。
        CrashReportBreadcrumbs.setDemoFlags(DemoConfig.current().snapshot())
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
        if (DemoConfig.isOn(Anr.ANR_03)) {
            // [ANR-03] 統計ストアの遅延初期化を、起動と同時にワーカースレッドで先回りさせる。
            //  「重い初期化はメイン外へ」という判断自体は ANR-02 の処方どおりで正しい。
            //  だが StatsStore.warmUp は初期化の全時間ロックを握るため、起動直後に
            //  ディープリンクで統計画面まで来たメインが monitor 待ちで凍る（waiting系）。
            //  スレッド名はトレースの `held by "stats-store-warmup"` に出る＝犯人の名札。
            thread(name = "stats-store-warmup") { StatsStore.warmUp() }
        }
        // [ANR-05] 「誰に起こされたか」は下の2か所で使うので一度だけ問い合わせる
        //  （Binder 往復を二重にしない。トグル OFF なら問い合わせ自体を行わない）。
        val isAnr05On = DemoConfig.isOn(Anr.ANR_05)
        val isBackgroundStart = isAnr05On && StartupOrigin.isBackgroundStart(this)
        if (isAnr05On
            && isBackgroundStart
            && !StartupOrigin.lastExitWasAnr(this)
        ) {
            // [ANR-05] 背面で起こされたついでに溜まった仕事を片付ける:
            //  未送信レポートのインデックス再構築（約+10秒の実作業）。
            //
            //  **背面起動には入力が無い＝入力ディスパッチ5秒の番犬は鳴かない。**
            //  onCreate を見張っているのは AMS の bindApplication 締切（15秒 ×
            //  ro.hw_timeout_multiplier）だけで、これを破ると ANR ダイアログすら出ずに
            //  無言で kill される（Reason: Process ... failed to complete startup）。
            //  ANR-02 の6つ（6.6〜8.0秒）だけでは15秒に届かない。この1行が乗って初めて越える。
            //  ＝「単独犯ではなく総量」という ANR-02 の教訓が、締切の種類を変えて再演される。
            //
            //  第3項は安全弁: 直前が ANR 死なら今回は重くしない。背面 ANR 死 →
            //  ジョブ再スケジュール → また起こされてまた死ぬ、の無限ループを断ち、
            //  デモ機が二度と開けなくなる事態（文鎮化）も構造的に防ぐ。鳴るのは1回武装につき1発。
            //  処方は ANR-02 と同じ「onCreate は予約だけ」＝ StartupGate.runOnWorkerThread。
            UnsentReportIndexInitializer.init()
        }
        startKoin {
            androidContext(this@DorodoroApplication)
            modules(appModule)
        }
        if (isAnr05On && !isBackgroundStart) {
            // [ANR-05] ANRログ送信を模して Work を enqueue する＝**種蒔き**。
            //  ここ自体は無実（doWork も軽量なまま）。役割は「アプリが死んだ後にプロセスを
            //  起こす仕掛け」を仕込むことだけで、事故は起こされた先の onCreate（上の分岐）で起きる。
            //
            //  **前面起動のときだけ**張り直すのが肝。背面起動＝いま自分を起こしてくれた Work が
            //  実行されようとしている最中なので、そこで同じ一意名を触ると自分の目覚ましを
            //  壊してしまう（REPLACE ならキャンセル、KEEP なら no-op、APPEND なら次が遠のく。
            //  3通り全部踏んだ記録が AnrLogUploadScheduler の KDoc にある）。
            //  触らなければ REPLACE で単純に「常にちょうど1つ・20秒後」に保てる。
            //
            //  ANR-02 トグルも ON にして、アプリを BG に落とし `am kill` してから発火させること
            //  （`am force-stop` はジョブごと消えるので不可）。`scripts/demo-anr05.sh` が自動化済み。
            AnrLogUploadScheduler.enqueue(this)
        }
        // onCreate の末尾に置く: 同じ prefs を DemoConfig.isOn が既にロード済みなので
        // ここで読んでも違反が出ず、起動時違反のトレースの帰属も変わらない。
        StrictModeBannerSettings.init(this)
    }
}
