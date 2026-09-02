package com.pelantica.dorodorotimer.core.report

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pelantica.dorodorotimer.core.debug.DemoFlagsState

/**
 * Crashlytics のレポートに「どういう状態で・何を操作して起きたか」を添えるパンくず。
 * カスタムキー（最新状態: demoMode のトグル・現在タブ）とログ（時系列: タブ切替の履歴）を使い分ける。
 *
 * ANR にも効くのは、Crashlytics の ANR 報告が ApplicationExitInfo を次回起動時に読み、
 * その ANR が起きたセッションの保存物（ここで積んだキーとログ）からレポートを組み立てるため。
 * 逆に、待って自然回復した ANR はプロセスが死なないのでレポート自体が作られない。
 *
 * 書き込みは Crashlytics のワーカースレッドに積まれるだけで、メインスレッドから呼んでも
 * ディスク I/O は発生しない（StrictMode にも掛からない）。
 */
object CrashReportBreadcrumbs {

    /**
     * Firebase 未初期化（ユニットテスト等）では [FirebaseCrashlytics.getInstance] が
     * RuntimeException 系で失敗するため吸収して null を返す。無くてもアプリの動作には関係ない。
     */
    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (_: RuntimeException) {
            null
        }

    /**
     * demoMode のトグル状態をカスタムキーに載せる（キー名は SharedPreferences と同じ）。
     * `Application.onCreate` の ANR-02 分岐より前に呼ぶこと: 起動フリーズ中のセッションにもキーが乗る。
     */
    fun setDemoFlags(state: DemoFlagsState) {
        val c = crashlytics ?: return
        c.setCustomKey("demo_master", state.master)
        state.perAnr.forEach { (anr, on) -> c.setCustomKey(anr.name.lowercase(), on) }
    }

    /** 表示タブの変化を記録する（ログに時系列、カスタムキー `current_tab` に最後のタブ）。 */
    fun tabShown(tabName: String) {
        val c = crashlytics ?: return
        c.setCustomKey("current_tab", tabName)
        c.log("tab -> $tabName")
    }
}
