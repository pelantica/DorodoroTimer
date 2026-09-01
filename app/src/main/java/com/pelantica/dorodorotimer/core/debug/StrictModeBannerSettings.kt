package com.pelantica.dorodorotimer.core.debug

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * StrictMode バナーの表示ミュート設定。ミュートするのは表示だけで、検出・
 * [StrictModeViolations] への記録・logcat 出力は止めない。StrictMode のポリシーごと
 * OFF にする作りにすると「起動時に自分の DemoConfig.init が捕まる」自己検出デモが壊れる。
 *
 * 永続化先は demoMode と同じ prefs ファイル（[SharedPrefsDemoFlags.PREFS_NAME]）。
 * 別ファイルにすると初回ロードが起動時の DiskReadViolation を1件増やすため。
 */
object StrictModeBannerSettings {

    private const val KEY_MUTED = "strictmode_banner_muted"

    private val _muted = MutableStateFlow(false)
    /** true の間、バナーを表示しない（記録は続く）。既定 false。 */
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private var prefs: SharedPreferences? = null

    /**
     * Application.onCreate の末尾で呼ぶ。DemoConfig.init より前に置くと prefs 初回ロードの
     * 待ち役になり、起動時違反のスタックトレースの帰属がこちらへ移ってしまう。
     */
    fun init(context: Context) {
        val p = context.applicationContext
            .getSharedPreferences(SharedPrefsDemoFlags.PREFS_NAME, MODE_PRIVATE)
        prefs = p
        _muted.value = p.getBoolean(KEY_MUTED, false)
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        prefs?.edit()?.putBoolean(KEY_MUTED, muted)?.apply()
    }
}
