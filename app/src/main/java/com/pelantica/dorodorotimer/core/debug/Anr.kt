package com.pelantica.dorodorotimer.core.debug

/**
 * demoMode で個別に ON/OFF できる ANR 事例の識別子。key は name.lowercase()。
 *
 * @property requiresRestart トグル変更の反映にアプリ再起動が必要か。起動時に一度だけ
 *  配線される系（Koin `single` の差し替え・`Application.onCreate` 内の分岐）は true、
 *  使うたびにフラグを読むだけで完結する系（`onReceive` / `startForeground` 直前など）は false。
 */
enum class Anr(val requiresRestart: Boolean) {
    /** [ANR-01] DB ライブラリの差（同期実行 vs suspend）。DI（Koin single）で正版/ANR版を差し替える。 */
    ANR_01(requiresRestart = true),

    /** [ANR-02] Application.onCreate での重い同期初期化。 */
    ANR_02(requiresRestart = true),

    /**
     * [ANR-03] 起動 × ロック競合。統計ストアのウォームアップ（ロックを握る側）を
     * Application.onCreate で起動するかを決める。
     */
    ANR_03(requiresRestart = true),

    /** [ANR-04] 起動時に鍵庫から鍵を同期ロードするか（Application.onCreate 内で読む）。 */
    ANR_04(requiresRestart = true),

    /**
     * [ANR-05] 背面起動ブースト。ANRログ送信 Work を種蒔きし、その Work に背面で起こされた
     * 起動だけ約10秒メインを占有して bindApplication の15秒制限を破り、
     * ダイアログなしの無言 kill（`failed to complete startup`）を起こす。
     */
    ANR_05(requiresRestart = true),

    /** [ANR-06] BroadcastReceiver#onReceive 内で毎回読む。再起動不要。 */
    ANR_06(requiresRestart = false),

    /** [ANR-07] 起動時クラスロード集中（Application.onCreate 系）。 */
    ANR_07(requiresRestart = true),

    /** [ANR-FGS] startForeground 呼び出し直前で毎回読む。再起動不要。 */
    ANR_FGS(requiresRestart = false),
}
