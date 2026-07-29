package com.tefumichangdev.dorodorotimer.app.startup

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
 * **実機校正で踏んだ罠**: 値を毎回同じ文字列（`"default_value_$i"`）で書くと、2回目以降の起動では
 * `SharedPreferencesImpl` が「前回コミット済みの値と同じ＝変更なし」と判定してディスク書き込み自体を
 * スキップしてしまい、初回だけ約930ms、2回目以降は約10msまで激減して**再現性がなくなった**
 * （実測：1回目 928ms → 2回目 12ms、3回目 8ms）。[com.tefumichangdev.dorodorotimer.data.local.stats.RawSqliteStatsHelper.reseedForDemo]
 * が「毎回リセットして入れ直す」のと同じ理屈で、値に [runMarker] を混ぜて起動毎に必ず内容を変え、
 * 毎回本物のディスク書き込みが起きるようにしている。
 */
internal object RemoteConfigInitializer {

    private const val TAG = "RemoteConfigInitializer"
    private const val PREFS_NAME = "remote_config_defaults"

    /**
     * [ANR-02] commit() の連続呼び出し回数。
     *
     * 実機校正の記録（エミュ API 36 / 2026-07-30、[runMarker] で値を毎回変えた後の計測）:
     *  - 1,200回 → 約1.5〜1.6秒（3回計測: 1501ms/1571ms/1561ms）。この値を採用。
     * [runMarker] を混ぜる前（固定値で書いていた時点）は 1回目 928ms → 2回目以降 8〜12ms まで
     * 激減する不具合があった（KDoc本文参照）。端末が変われば再校正する。
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
