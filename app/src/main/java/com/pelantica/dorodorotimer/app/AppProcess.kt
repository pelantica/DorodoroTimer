package com.pelantica.dorodorotimer.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * 実行中のプロセスがアプリの**メインプロセス**かどうかを判定する。
 *
 * [ANR-04] で鍵庫を `android:process=":vault"` に置いたことで、このアプリは複数プロセスになった。
 * `Application` のサブクラスは**プロセスごとに1インスタンス生成され、onCreate も各プロセスで走る**
 * ——これは見落としやすい落とし穴で、放っておくと `:vault` プロセスでも Koin 起動・
 * StrictMode 導入・起動時初期化（ANR-02/03/05 の仕掛け）が丸ごと走ってしまう。
 * 別プロセスで重い初期化が走ると `:vault` の起動が遅れ、bind の完了も遅れて
 * ANR-04 の再現が濁る（どのプロセスのトレースを読んでいるのかも分からなくなる）。
 *
 * なので [DorodoroApplication] は、メインプロセス以外では何もせずに抜ける。
 */
internal object AppProcess {

    /** メインプロセス（プロセス名 == パッケージ名）なら true。判定できない場合は安全側の true。 */
    fun isMainProcess(context: Context): Boolean {
        val name = currentProcessName(context) ?: return true
        return name == context.packageName
    }

    private fun currentProcessName(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            // API 26-27 には Application.getProcessName() が無い。この用途（自プロセスの名前）なら
            // getRunningAppProcesses でも取れる（他アプリの一覧が取れなくなる制限は Q 以降）。
            context.getSystemService(ActivityManager::class.java)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
}
