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
import com.pelantica.dorodorotimer.vendor.securevault.SecureVaultKeyBootLoader
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DorodoroApplication : Application() {
    /** アプリ全体の寿命を持つスコープ。[ANR-03][正版] の `warmUpReactive` 起動にだけ使う。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Application.onCreate はプロセスごとに走る。[ANR-04] の鍵庫が :vault プロセスにあるため、
        // メインプロセス以外では初期化一式を素通りさせる（詳細は AppProcess の KDoc）。
        if (!AppProcess.isMainProcess(this)) return
        // デバッグビルドでは常にメインスレッドのI/Oを見張る。onCreate の先頭に置き、
        // 直後の DemoConfig.init（SharedPreferences の読み込み）も観測対象に含める。
        StrictModeInstaller.install()
        DemoConfig.init(this)
        // demoMode のトグル状態を Crashlytics のカスタムキーに載せる。
        // ANR-02 の分岐より前に置く: 起動フリーズ中のセッションにもキーが乗るように。
        CrashReportBreadcrumbs.setDemoFlags(DemoConfig.current().snapshot())
        // [ANR-02] 「SDK風」初期化6つを、どのスレッドで走らせるか。顔ぶれ・順番・作業量は
        //  StartupGate.runAll にだけあり、ANR するかしないかの差分はこの呼び分けの1行だけ。
        if (DemoConfig.isOn(Anr.ANR_02)) {
            // [ANR-02] メインで同期実行。6つの合計が起動ANRのしきい値（入力ディスパッチ5秒）を
            //  超える。単独犯ではなく初期化の総量が原因。
            StartupGate.runOnMainThread(this)
        } else {
            // 正版: 同じ6つをワーカースレッドへ「予約」して即返す。
            StartupGate.runOnWorkerThread(this)
        }
        if (DemoConfig.isOn(Anr.ANR_03)) {
            // [ANR-03] 統計ストアの遅延初期化をワーカースレッドで先回りさせる。判断自体は正しいが、
            //  StatsStore.warmUp は初期化の全時間ロックを握るため、起動直後にディープリンクで
            //  統計画面まで来たメインが monitor 待ちで凍る（waiting系）。
            //  スレッド名はトレースの `held by "stats-store-warmup"` に出る＝犯人の名札。
            thread(name = "stats-store-warmup") { StatsStore.warmUp() }
        } else {
            // [ANR-03][正版] launch するだけ＝ onCreate は即返る。誰も待たない。
            //  準備完了は StatsStore.readiness（StateFlow）で流れる。
            appScope.launch { StatsStore.warmUpReactive() }
        }
        if (DemoConfig.isOn(Anr.ANR_04) && !StartupOrigin.lastExitWasAnr(this)) {
            // [ANR-04] 保存済み集中記録の復号鍵を鍵庫(:vault)から同期 Binder IPC で取得する。
            //  返事をメインで待ち込むので onCreate が固まり、bindApplication の番犬(15秒)が
            //  ダイアログなしで無言 kill する（waiting/binder）。
            //  安全弁: 直前が ANR 死なら今回は待たない（再起動で必ず抜けられる＝文鎮化防止）。
            SecureVaultKeyBootLoader.loadKeyBlocking(this)
        }
        // [ANR-05] 「誰に起こされたか」は下の2か所で使うので一度だけ問い合わせる。
        val isAnr05On = DemoConfig.isOn(Anr.ANR_05)
        val isBackgroundStart = isAnr05On && StartupOrigin.isBackgroundStart(this)
        if (isAnr05On
            && isBackgroundStart
            && !StartupOrigin.lastExitWasAnr(this)
        ) {
            // [ANR-05] 背面で起こされた起動でだけ、未送信レポートのインデックス再構築を上乗せする。
            //  ANR-02 の6つでは届かない bindApplication の15秒締切を、この1行が乗って初めて越える。
            //  第3項は安全弁（直前が ANR 死なら重くしない＝無限ループと文鎮化の防止）。
            UnsentReportIndexInitializer.init()
        }
        startKoin {
            androidContext(this@DorodoroApplication)
            modules(appModule)
        }
        if (isAnr05On && !isBackgroundStart) {
            // [ANR-05] ANRログ送信を模した Work の enqueue＝種蒔き。事故は起こされた先の
            //  onCreate（上の分岐）で起きる。前面起動のときだけ張り直すのが肝: 背面起動から
            //  同じ一意名を触ると、自分を起こしてくれた Work を壊す（AnrLogUploadScheduler の KDoc）。
            AnrLogUploadScheduler.enqueue(this)
        }
        // onCreate の末尾に置く: 同じ prefs を DemoConfig.isOn が既にロード済みなので
        // ここで読んでも StrictMode 違反が出ない。
        StrictModeBannerSettings.init(this)
    }
}
