package com.tefumichangdev.dorodorotimer.core.debug

import android.content.Context

/**
 * 登壇デモ用の「ANR再現モード」フラグ管理。
 *
 * - OFF（リリース既定）: 正しい（メインセーフな）実装経路を通る。
 * - ON: わざとANRを誘発する実装経路を通る。
 *
 * 分岐は原則 DI（Koin）で実装ごと差し替える。DIで差し替えにくい局所（commit/apply,
 * onReceive 内、Application.onCreate の初期化順序、startForeground の有無 など）だけ、
 * その場で `if (DemoConfig.isOn(Anr.ANR_xx)) { /* [ANR-xx] */ }` と参照する。
 *
 * [init] を Application.onCreate の最初に呼ぶことで SharedPreferences 永続化が有効になる。
 * 呼ぶ前（または [setFlagsForTest] で null を設定した場合）は false を返す（クラッシュしない）。
 */
object DemoConfig {

    @Volatile
    private var flags: DemoFlags? = null

    /** Application.onCreate の最初に呼ぶ（同期）。 */
    fun init(context: Context) {
        flags = SharedPrefsDemoFlags(context.applicationContext)
    }

    /** テスト用に差し替え可能にする。null を渡すと未初期化状態に戻る。 */
    fun setFlagsForTest(f: DemoFlags?) {
        flags = f
    }

    private fun requireFlags(): DemoFlags = flags ?: error("DemoConfig.init not called")

    /** Koin などから同一インスタンスを取得するための公開アクセサ。init 前に呼ぶと例外になる。 */
    fun current(): DemoFlags = requireFlags()

    /** master AND 個別。各ANRフックはこれでガードする。 */
    fun isOn(anr: Anr): Boolean {
        val f = flags ?: return false
        return f.isMasterOn() && f.isOn(anr)
    }

    /** 後方互換: 旧コードの DemoConfig.enabled は master と同義。 */
    var enabled: Boolean
        get() = flags?.isMasterOn() ?: false
        set(value) { flags?.setMaster(value) }
}
