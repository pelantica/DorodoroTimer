package com.pelantica.dorodorotimer.app.startup

import android.content.Context

/**
 * [ANR-02] リモートコンフィグSDK風の初期化。起動時の「デフォルト設定値を SharedPreferences に
 * 同期書き込みして確定させる」を、`commit()`（同期・呼んだスレッドをブロック）の連続呼び出しで模す。
 * 呼び出し側の1行からは `commit()` か `apply()` かは分からない。
 *
 * 値に runMarker を混ぜるのは、毎回同じ値だと `SharedPreferencesImpl` が「変更なし」と判定して
 * ディスク書き込みをスキップし、2回目以降の再現性が消えるため。
 */
internal object RemoteConfigInitializer {

    private const val TAG = "RemoteConfigInitializer"
    private const val PREFS_NAME = "remote_config_defaults"

    /** [ANR-02] commit() の連続呼び出し回数。端末が変われば再校正する。 */
    internal const val COMMIT_ITERATIONS = 1_200

    fun init(context: Context, iterations: Int = COMMIT_ITERATIONS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // [ANR-02] 起動毎に値を変えて「前回と同じ値だから書き込み省略」を起こさせない。
            val runMarker = System.nanoTime()
            repeat(iterations) { i ->
                // [ANR-02] commit() は書き込み完了までブロックする（apply() は非同期）。
                prefs.edit().putString("default_key_$i", "default_value_$i-$runMarker").commit()
            }
        }
    }
}
