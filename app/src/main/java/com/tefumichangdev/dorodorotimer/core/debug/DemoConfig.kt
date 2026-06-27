package com.tefumichangdev.dorodorotimer.core.debug

/**
 * 登壇デモ用の「ANR再現モード」フラグ。
 *
 * - OFF（リリース既定）: 正しい（メインセーフな）実装経路を通る。
 * - ON: わざとANRを誘発する実装経路を通る。
 *
 * 分岐は原則 DI（Koin）で実装ごと差し替える。DIで差し替えにくい局所（commit/apply,
 * onReceive 内、Application.onCreate の初期化順序、startForeground の有無 など）だけ、
 * その場で `if (DemoConfig.enabled) { /* [ANR-xx] */ }` と参照する。
 *
 * 骨格では揮発フラグ。実装時に DataStore 永続化＋設定画面トグルへ差し替える。
 */
object DemoConfig {
    @Volatile
    var enabled: Boolean = false
}
