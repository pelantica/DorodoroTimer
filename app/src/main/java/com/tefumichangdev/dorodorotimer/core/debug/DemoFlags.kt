package com.tefumichangdev.dorodorotimer.core.debug

import android.content.Context
import android.content.Context.MODE_PRIVATE

/** demoMode フラグのスナップショット。 */
data class DemoFlagsState(
    val master: Boolean,
    val perAnr: Map<Anr, Boolean>,
    /** 直近トグルした [Anr.requiresRestart] == true の事例。非null の間、再起動を促すダイアログを表示する。 */
    val restartPromptFor: Anr? = null,
)

/** demoMode フラグへのアクセスインターフェース。テストでは FakeDemoFlags で差し替える。 */
interface DemoFlags {
    fun isMasterOn(): Boolean
    /** master は参照せず、個別フラグのみを返す。master との AND は DemoConfig 側で行う。 */
    fun isOn(anr: Anr): Boolean
    fun setMaster(on: Boolean)
    fun setOn(anr: Anr, on: Boolean)
    fun snapshot(): DemoFlagsState
}

/**
 * SharedPreferences を使った永続化実装。
 * - prefs 名: "demo_flags"
 * - master キー: "demo_master"
 * - 各 ANR キー: `anr.name.lowercase()` (例: "anr_01")
 * - 既定 false。書き込みは apply()（commit() は ANR 事例のため使わない）。
 */
class SharedPrefsDemoFlags(context: Context) : DemoFlags {

    private val prefs = context.applicationContext
        .getSharedPreferences("demo_flags", MODE_PRIVATE)

    override fun isMasterOn(): Boolean = prefs.getBoolean(KEY_MASTER, false)

    override fun isOn(anr: Anr): Boolean = prefs.getBoolean(anr.name.lowercase(), false)

    override fun setMaster(on: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER, on).apply()
    }

    override fun setOn(anr: Anr, on: Boolean) {
        prefs.edit().putBoolean(anr.name.lowercase(), on).apply()
    }

    override fun snapshot(): DemoFlagsState = DemoFlagsState(
        master = isMasterOn(),
        perAnr = Anr.entries.associateWith { isOn(it) },
    )

    companion object {
        private const val KEY_MASTER = "demo_master"
    }
}
