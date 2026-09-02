package com.pelantica.dorodorotimer.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * 実行中のプロセスがアプリのメインプロセスかどうかを判定する。
 *
 * [ANR-04] の鍵庫が `android:process=":vault"` にあるためこのアプリは複数プロセスで、
 * `Application.onCreate` はプロセスごとに走る。放っておくと `:vault` でも Koin 起動や
 * 起動時初期化が丸ごと再実行されるので、[DorodoroApplication] はメインプロセス以外では即抜ける。
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
            // API 26-27 には Application.getProcessName() が無い。自プロセスの名前なら
            // getRunningAppProcesses で取れる。
            context.getSystemService(ActivityManager::class.java)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
}
