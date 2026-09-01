package com.pelantica.dorodorotimer.app.startup

import android.content.Context

/**
 * [ANR-02] リモートコンフィグSDK風の初期化。実SDKが起動時にやりがちな「デフォルト設定値を
 * SharedPreferences に同期書き込みして確定させる」を、`commit()`（同期・呼んだスレッドをブロック）
 * の連続呼び出し（I/Oバウンド）で模す。
 *
 * `apply()` なら非同期でメインスレッドを塞がないところを、あえて `commit()` を使うのが
 * 「ラッパーが隠す意図せぬI/O」のポイント（呼び出し側の1行 `RemoteConfigInitializer.init(context)`
 * からは commit() を使っているかどうか分からない）。
 *
 * 処方: Koin lazyModule 化、あるいは初期化自体を必要時まで先送りする。`commit()` を `apply()` に
 * 変えるだけでも改善するが、根本的には初期化のタイミングを見直すべき。
 *
 * **[runMarker] を混ぜている理由**: 値を毎回同じ文字列（`"default_value_$i"`）で書くと、
 * `SharedPreferencesImpl` が「前回コミット済みの値と同じ＝変更なし」と判定してディスク書き込み自体を
 * スキップする。実測では初回だけ約930ms、2回目以降は約10msまで落ちて再現性がなくなった。
 * [com.pelantica.dorodorotimer.data.local.stats.RawSqliteStatsHelper.reseedForDemo] が
 * 「毎回リセットして入れ直す」のと同じ理屈で、起動毎に必ず内容を変えて本物の書き込みを起こさせる。
 */
internal object RemoteConfigInitializer {

    private const val TAG = "RemoteConfigInitializer"
    private const val PREFS_NAME = "remote_config_defaults"

    /**
     * [ANR-02] commit() の連続呼び出し回数。エミュ API 36 で約1.5〜1.6秒になる値。
     * 端末が変われば再校正する。
     */
    internal const val COMMIT_ITERATIONS = 1_200

    fun init(context: Context, iterations: Int = COMMIT_ITERATIONS) { // [ANR-02]
        StartupWork.timed(TAG, "init") {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // [ANR-02] 起動毎に値を変えて「前回と同じ値だから書き込み省略」を起こさせない（後述KDoc参照）。
            val runMarker = System.nanoTime()
            repeat(iterations) { i ->
                // [ANR-02] commit() は書き込み完了までブロックする（apply() は非同期）。
                prefs.edit().putString("default_key_$i", "default_value_$i-$runMarker").commit()
            }
        }
    }
}
