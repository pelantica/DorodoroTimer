package com.pelantica.dorodorotimer.core.debug

/**
 * demoMode で個別にON/OFFできるANR事例の識別子。key は name.lowercase()。
 *
 * @property requiresRestart トグルを変更した効果が反映されるのにアプリの再起動が必要か。
 *  - true: 起動時に一度だけ配線される系（Koin `single` のDI差し替え、`Application.onCreate`
 *    内での分岐など）。`single` はキャッシュされ、`onCreate` は既に実行済みのため、
 *    プロセスを再起動するまでトグルの変更が反映されない。**発火点が毎回フラグを読む場合でも、
 *    その相方が起動時にしか配線されないなら true**（例: ANR_03 の画面側は毎回読むが、
 *    待つ相手のウォームアップは `onCreate` でしか走らない）。
 *  - false: 使うたびにフラグを読み、それだけで完結する系（`onReceive` /
 *    `startForeground` 呼び出し直前 など）。再起動不要で次回発火時から反映される。
 */
enum class Anr(val requiresRestart: Boolean) {
    /** DI（Koin single）で正版/ANR版を差し替える想定（事例①）。single はキャッシュされるため再起動が必要。 */
    ANR_01(requiresRestart = true),

    /** Application.onCreate 内で読む（起動時の重い同期初期化）。onCreate は起動時に一度しか走らない。 */
    ANR_02(requiresRestart = true),

    /**
     * 統計ストアのウォームアップを Application.onCreate で起動するか判定する（起動 × ロック競合）。
     * 画面側（LaunchedEffect）でも毎回読むが、待つ相手のロックを握るウォームアップが
     * onCreate でしか走らないため、トグルの効果は次のプロセス起動から。
     */
    ANR_03(requiresRestart = true),

    /**
     * Application.onCreate 内で読む（起動時に鍵庫から鍵を同期ロードするかの選択）。
     * onCreate は起動時に一度しか走らないため、トグルの効果は次のプロセス起動から
     * ＝再起動が必要。
     */
    ANR_04(requiresRestart = true),

    /**
     * 背面起動ブースト（Application.onCreate 内で読む）。ON のとき onCreate は2つのことをする:
     *  1. ANRログ送信 Work を enqueue する＝**種蒔き**（死んだプロセスを後で起こす仕掛け）。
     *  2. その Work / アラームに**背面で起こされた起動**だったときだけ、
     *     「未送信レポートのインデックス再構築」で約10秒メインを占有する。
     *
     * 前面起動には入力ディスパッチ5秒しか番犬がいないが、背面起動を見張るのは
     * bindApplication の15秒。ANR-02 の6.6〜8.0秒に上乗せしてこれを破ると、
     * ダイアログなしの無言 kill になる（`failed to complete startup`）。
     *
     * onCreate は起動時に一度しか走らないので再起動が必要。
     */
    ANR_05(requiresRestart = true),

    /** BroadcastReceiver#onReceive 内で毎回読む。再起動不要。 */
    ANR_06(requiresRestart = false),

    /** Application.onCreate 系（起動時クラスロード集中）。onCreate は起動時に一度しか走らない。 */
    ANR_07(requiresRestart = true),

    /** startForeground 呼び出し直前で毎回読む。再起動不要。 */
    ANR_FGS(requiresRestart = false),
}
