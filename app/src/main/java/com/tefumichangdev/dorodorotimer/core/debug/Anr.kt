package com.tefumichangdev.dorodorotimer.core.debug

/**
 * demoMode で個別にON/OFFできるANR事例の識別子。key は name.lowercase()。
 *
 * @property requiresRestart トグルを変更した効果が反映されるのにアプリの再起動が必要か。
 *  - true: 起動時に一度だけ配線される系（Koin `single` のDI差し替え、`Application.onCreate`
 *    内での分岐など）。`single` はキャッシュされ、`onCreate` は既に実行済みのため、
 *    プロセスを再起動するまでトグルの変更が反映されない。
 *  - false: 使うたびにフラグを読む系（`onReceive` / Composable の `LaunchedEffect` /
 *    `startForeground` 呼び出し直前 など）。再起動不要で次回発火時から反映される。
 */
enum class Anr(val requiresRestart: Boolean) {
    /** DI（Koin single）で正版/ANR版を差し替える想定（事例①）。single はキャッシュされるため再起動が必要。 */
    ANR_01(requiresRestart = true),

    /** Application.onCreate 内で読む（起動時の重い同期初期化）。onCreate は起動時に一度しか走らない。 */
    ANR_02(requiresRestart = true),

    /** ディープリンク受信時・LaunchedEffect 内で毎回読む。再起動不要。 */
    ANR_03(requiresRestart = false),

    /** Application.onCreate 内で読み、ANRログ送信 Work を enqueue するか判定する。 */
    ANR_05(requiresRestart = true),

    /** BroadcastReceiver#onReceive 内で毎回読む。再起動不要。 */
    ANR_06(requiresRestart = false),

    /** Application.onCreate 系（起動時クラスロード集中）。onCreate は起動時に一度しか走らない。 */
    ANR_07(requiresRestart = true),

    /** startForeground 呼び出し直前で毎回読む。再起動不要。 */
    ANR_FGS(requiresRestart = false),
}
