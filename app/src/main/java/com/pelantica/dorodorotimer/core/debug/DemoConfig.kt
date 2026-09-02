package com.pelantica.dorodorotimer.core.debug

import android.content.Context

/**
 * デモ用の「ANR再現モード」フラグ管理。マスタートグル AND 個別トグルが成立したときだけ
 * ANR 誘発経路を通り、OFF（リリース既定）では常に正しいメインセーフな実装経路を通る。
 * 分岐は DI（Koin）での実装差し替え、または局所の `if (DemoConfig.isOn(Anr.ANR_xx)) { /* [ANR-xx] */ }`。
 *
 * [init] を Application.onCreate の最初に呼ぶと SharedPreferences 永続化が有効になる。
 * 呼ぶ前は [isOn] が false を返す（クラッシュしない）。
 */
object DemoConfig {

    @Volatile
    private var flags: DemoFlags? = null

    /** Application.onCreate の最初に呼ぶ（同期）。 */
    fun init(context: Context) {
        flags = SharedPrefsDemoFlags(context.applicationContext)
    }

    /** テスト用の差し替え。null で未初期化状態に戻る。 */
    fun setFlagsForTest(f: DemoFlags?) {
        flags = f
    }

    private fun requireFlags(): DemoFlags = flags ?: error("DemoConfig.init not called")

    /** 同一インスタンスの公開アクセサ。init 前に呼ぶと例外。 */
    fun current(): DemoFlags = requireFlags()

    /** master AND 個別。各 ANR フックはこれでガードする。 */
    fun isOn(anr: Anr): Boolean {
        val f = flags ?: return false
        return f.isMasterOn() && f.isOn(anr)
    }

    var enabled: Boolean
        get() = flags?.isMasterOn() ?: false
        set(value) { flags?.setMaster(value) }
}
