package com.pelantica.dorodorotimer.core.report

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pelantica.dorodorotimer.core.debug.DemoFlagsState

/**
 * Crashlytics のレポートに「その ANR/クラッシュが、どういう状態で・何を操作して起きたか」を
 * 添えるためのパンくず。2種類を使い分ける:
 *
 * - **カスタムキー**（最新状態）: demoMode のトグル状態・現在のタブ。レポートの「キー」欄に
 *   最後の値だけが出る。「どのトグルが ON のセッションだったか」が一目で分かる。
 * - **ログ**（時系列）: タブ切替の履歴。レポートの「ログ」欄に時刻付きで並び、
 *   ANR までの操作の流れ（timer → stats → …）を辿れる。
 *
 * ANR にも効く理由: Crashlytics の ANR 報告は ApplicationExitInfo（ANR でプロセスが
 * 殺された記録）を**次回起動時**に読んで、その ANR が起きたセッションの保存物
 * （ここで積んだキーとログを含む）からレポートを組み立てるため。
 * 逆に言うと、ユーザーが待って自然回復した ANR はプロセスが死なないので
 * そもそもレポート自体が作られない（このパンくず以前の話として届かない）。
 *
 * 書き込みはどちらも Crashlytics 内部のワーカースレッドに積まれるだけなので、
 * メインスレッドから呼んでもディスク I/O は発生しない（StrictMode にも掛からない）。
 */
object CrashReportBreadcrumbs {

    /**
     * Firebase 未初期化なら null。ユニットテストでは FirebaseInitProvider が走らないため
     * [FirebaseCrashlytics.getInstance] が失敗する: Robolectric なら IllegalStateException、
     * 素の JVM テストなら `android.os.Process.myPid` が未モックで RuntimeException になる
     * （FirebaseApp が例外メッセージを組み立てる過程で Android API を触るため）。
     * どちらもここで吸収する。パンくずは診断の付加情報であり、無くてもアプリの動作には関係ない。
     */
    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (_: RuntimeException) {
            null
        }

    /**
     * demoMode のトグル状態をカスタムキーに載せる。キー名は SharedPreferences と同じ
     * `demo_master` / `anr_01` … に揃える（レポートと実装を突き合わせやすいように）。
     * `Application.onCreate` の ANR-02 分岐より前に呼ぶこと: 起動フリーズ中の
     * セッションにもキーが乗る。
     */
    fun setDemoFlags(state: DemoFlagsState) {
        val c = crashlytics ?: return
        c.setCustomKey("demo_master", state.master)
        state.perAnr.forEach { (anr, on) -> c.setCustomKey(anr.name.lowercase(), on) }
    }

    /**
     * 表示タブの変化を記録する。ログに `tab -> STATS` の形で時系列が残り、
     * カスタムキー `current_tab` には最後に見ていたタブが残る。
     */
    fun tabShown(tabName: String) {
        val c = crashlytics ?: return
        c.setCustomKey("current_tab", tabName)
        c.log("tab -> $tabName")
    }
}
