# Dorodoro Timer 🍅⏱

DroidKaigi 2026 セッション **「あなたのANRはどこから？ — 発生する仕組みを診断し、症状別に処方する」** の登壇用サンプルアプリ。

「集中作業用タイマー（ポモドーロ）」という**普通のアプリ**のコードに、ANR の種になる実装を**自然な形で埋め込み**、各所のマーカーコメント（`// [ANR-xx] ...`）と、この README の対応表でスライドの事例に紐づける。デモ用の不自然な発火ボタンは作らず、「**普通に操作すると固まる**」を見せる。

> 将来は「25分の縛りが鬱陶しい」を改善する方向で機能追加してリリース予定。DroidKaigi 後に KMP 化も視野。

設計の詳細・決定の経緯は Notion: **ANRサンプルアプリ（DorodoroTimer）設計メモ** を参照（真実の源）。

## demoMode（教材と製品の両立）

標準コードは**正しい（リリース品質）実装**。マスタートグル＋ ANR ごとの個別トグル（設定画面）があり、`DemoConfig.isOn(Anr.XX)`（= master AND 個別）が **ON のときだけ** ANR 誘発経路を通る。リリースは常に OFF。

- **分岐は原則 DI（Koin）で実装ごと差し替え**（呼び出し側はクリーン、before/after が実装ファイル単位で並ぶ）。
- DI で差し替えにくい局所だけ、その場に `if (DemoConfig.isOn(Anr.XX)) { /* [ANR-xx] */ }`。

## スライド事例 ↔ コード 対応表

> 軸: busy=作業中 / waiting=待たされ。行番号は実装が動くとずれるので、確実に辿るならマーカー `[ANR-xx]` で grep する。

| ANR-ID | 事例 | 軸 | 締切種別 | 配置 | 処方 | 状態 |
| --- | --- | --- | --- | --- | --- | --- |
| ANR-01 | メインスレッド I/O（生SQLite vs Room） | busy | input | `di/AppModule.kt:80`（DI差し替え）/ `data/local/stats/BlockingStatsRepository.kt:20` | Room の suspend DAO に任せる（守ってくれないライブラリは自前で `withContext(IO)`） | 実装済み |
| ANR-02 | Application.onCreate の重い初期化 | busy | 起動 | `app/DorodoroApplication.kt:37` / `app/startup/StartupGate.kt:58` | `StartupGate.runOnWorkerThread`（onCreate は予約だけ）。Koin `lazyModule` も候補 | 実装済み（正版込み） |
| ANR-03 | Deeplink 起動 × ロック競合 | waiting | input | `data/local/stats/StatsStore.kt:137`（ロック保持）/ `app/DorodoroApplication.kt:70`（BGでウォームアップ）/ `feature/stats/StatsScreen.kt:55`（メインの同期アクセス） | メインから同期アクセスしない（suspend 化して `withContext` で待つ）/ 初期化とロック保持の分離（ロック内は代入だけ）/ シングルトン遅延評価の設計。**正版**: 準備状態を `StateFlow` で公開し UI は Loading→Ready を観測（メインは待たない）／排他が要るなら `synchronized` ではなく `Mutex.withLock`（suspend で凍らせない）。出典: [ANR ドキュメント](https://developer.android.com/topic/performance/vitals/anr) | 実装済み（実機校正は登壇前TODO） |
| ANR-04 | Keystore 風の鍵生成（Binder + セキュアHW IPC） | waiting | 起動（bind application 15秒） | `app/DorodoroApplication.kt`（起動時に鍵庫から鍵を同期ロードする分岐）/ `vendor/securevault/SecureVaultKeyBootLoader.kt`（メインで transact する側）/ `vendor/securevault/SecureVaultService.kt`（`:vault` プロセスの待たせる側） | 起動クリティカルパス（onCreate）で鍵をメインスレッド同期取得しない。バックグラウンドで先読みし UI は即応答させる。相手はシステム／セキュアHWなので**速くする手段は無く、待ち方を変えるしかない** | 実装済み（正版込み） |
| ANR-05 | 背面起動 ANR（WorkManager / AlarmManager が起こす・ANR-02 連結） | busy | 起動（bind application 15秒） | `app/DorodoroApplication.kt:64`（分岐）/ `app/startup/StartupOrigin.kt:131`（背面判定）・`:173`（安全弁）/ `app/startup/UnsentReportIndexInitializer.kt:138`（+10.5秒）/ `service/work/AnrLogUploadScheduler.kt:47`（種蒔き） | ANR-02 と同じ「onCreate は予約だけ」＝ `StartupGate.runOnWorkerThread`。doWork 自体は軽量なまま（無罪） | 実装済み（実機E2E検証済み） |
| ANR-06 | BroadcastReceiver（onReceive 重処理） | busy/waiting | broadcast | `service/TimerAlarmReceiver.kt:22` / `service/ReceiverWork.kt:26` | `goAsync()` / 処理をメイン外へ | 実装済み（実機5秒超の最終校正は登壇前TODO） |
| ANR-07 | DexFile / ClassLoader（起動時集中） | busy/waiting | 起動 | _未_ | Koin `lazyModule` で遅延 | 未着手（速射枠） |
| ANR-FGS | ForegroundService の startForeground 5秒ルール | waiting | service | _未_（実体候補は `service/AmbientSoundService.kt`。現状 ANR フック無し） | 即 startForeground / 重い初期化を後へ | 未着手（CFP外・目玉候補） |

**正版（ANR-04 の処方）**: `vendor/securevault/SecureVaultKeyProvider.kt`（マーカー `[ANR-04][正版]`）。
`StatsViewModel.reload` から呼ばれ、鍵ロードを ①メイン外（`Dispatchers.IO`）②一度だけ生成してファイルキャッシュ
③統計画面を開くまで遅延、の3点で onCreate 同期版と対比させている。

**正版（ANR-03 の処方）**: `data/local/stats/StatsStore.kt` の `warmUpReactive` / `readiness`（マーカー `[ANR-03][正版]`）。
`DorodoroApplication.onCreate` は `appScope.launch` で予約するだけで誰も待たず、重い初期化はロックの外
（`Dispatchers.Default`）で回してロック内は代入だけにする。`StatsScreen` は `awaitReady()` を呼ばず
`readiness`（`StateFlow`）を observe するだけで、準備中は画面を覆わず上に小さなインジケータを出す。

### ⚠️ ANR-01 は端末スペック依存（高性能端末では出ないことがある）

ANR-01 のシード書き込みは**行数固定（5000行）**のため、高性能端末（例: Xiaomi 15T Pro / Dimensity 9400+）では一瞬で終わり、入力ディスパッチの5秒締切に届かず ANR にならないことがある。これは欠陥ではなく、**「同じコードでも端末スペックで ANR が出たり出なかったりする」という ANR の環境依存性の実例**（→ 事例③「ANRは環境に左右される」）。エミュレータや廉価端末では再現する。Vitals 採取など**端末非依存で確実に固めたい**場合は、時間基準で保持する ANR-03（25秒）や ANR-05 を使う。

### 連結レシピ：ANR-05 背面起動 ANR（ANR-02 と2つ ON）

設定画面で **master ON / ANR-02 ON / ANR-05 ON**（ANR-03 は OFF）にして再起動する。この時点で**前面起動は従来どおり生き残る**（約9〜12秒。ANR-02 の入力5秒は破るが文鎮化はしない）。ANR が出るのは**背面で起こされた起動だけ**で、そちらの締切は `bindApplication` の **15秒 × `ro.hw_timeout_multiplier`** ひとつしかない。

**いちばん簡単な再現（アラーム経路・adb 不要・操作は2つ）**:

1. タイマーを短く（1分など）にして**開始**
2. タスク一覧（Recents）からアプリを**スワイプで終了**（⚠️ 設定アプリの「強制停止」は仕掛けごと消えるので不可）
3. あとは放置。タイマー終了時刻にアラームがプロセスを起こし、**約15秒後に無言で kill** される（画面には何も出ない。証拠は下の「観測」）

**WorkManager 経路（1コマンド）**: `./scripts/demo-anr05.sh` — 武装 → kill → 目覚まし待ち → 起動理由の表示 → AEI の確認まで自動でやり、各段階を実況する。前回の残骸・生き残りプロセス・期限切れ Work があっても通るように書いてあるので、連続で何度でも回せる（1回あたり約1分）。スクリプトが吸収する端末側の事情:

- `am kill` は対象が cached に落ちるまで no-op なので、**pid が消えるまでリトライ**する（1発撃って sleep する方式だと取りこぼす）
- 非給電の端末は JobScheduler の**クォータ**が効き、デモを繰り返すと `WITHIN_QUOTA` が外れて鳴らなくなる → 給電中に見せかける（戻すには `adb shell cmd battery reset`）
- Android 15+ の**フレキシブルなジョブ実行**でまとめて実行されるため、初期遅延が切れてから実際に鳴るまで更に1〜2分かかる（実測 約102秒）→ フレックスを切り、遅延が切れたら強制実行で前に倒す（戻すには `adb shell cmd jobscheduler reset-flex-policy`）

**観測**（どちらの経路でも）:

```bash
adb shell getprop ro.hw_timeout_multiplier   # 空か 1 でなければ締切が15秒ではない
adb logcat | grep -E "ANR in|failed to complete startup|bg anr"
adb shell dumpsys activity exit-info com.pelantica.dorodorotimer
```

- ⚠️ **`am force-stop` は使わない**。ジョブもアラームも一緒に消えて、プロセスを起こす仕掛けが無くなる。プロセスを殺すのは `am kill`（cached になってから。前面のままだと効かない）。
- **背面 ANR はダイアログを出さない**。`Killing ... (adj 0): bg anr` で無言 kill され、痕跡は ApplicationExitInfo（`reason=6 (ANR) subreason=34 (BIND APPLICATION ANR)`。`anrInfo` が付く場合は `isUserPerceptible=false` も見える）と、次回起動時に Crashlytics が拾って送るレポートだけ。
- 目覚ましの Work は**前面起動のときだけ**張り直す（`ExistingWorkPolicy.REPLACE`）。背面起動から同じ一意名を触ると自分を起こした Work を壊すため。ポリシーを3通り試して踏んだ失敗は `service/work/AnrLogUploadScheduler.kt` の KDoc に記録した。
- 安全弁: 直前が ANR 死なら次の起動は重い初期化をスキップする（`StartupOrigin.lastExitWasAnr`）。再配送ループでの連続 ANR と、デモ機の文鎮化を防ぐ。**それでも開けなくなったら脱出は `adb shell pm clear com.pelantica.dorodorotimer`**（demoMode のフラグも消える）。

CFP 外の追加候補（重い同期計算 / Compose 再コンポーズ / ContentProvider 隠れ初期化 / 同期 Binder / wait-notify / commit() / Bitmap decode / 接続プール枯渇 / nativePollOnce の罠 等）は Notion のバックログに記録済み。採否は後日選定。

## 永続化（事例①の核：ライブラリのスレッド管理を見極める）

| ライブラリ | スレッドの扱い | 役割 |
| --- | --- | --- |
| Room | suspend/Flow DAO は IO へ逃す（守ってくれる側） | 「守ってくれる」例 |
| SQLDelight | クエリはデフォルト同期実行（守ってくれない側） | 「守ってくれない」例＝事例①の正体 |
| DataStore | suspend/Flow で非同期 | 補助 |

> ⚠️ SQLDelight は AGP 9 未対応のため現在**無効化中**（`.sq` と TODO は残置）。事例①の「守ってくれない」側は生SQLite（`data/local/stats/RawSqliteStatsHelper.kt` + `BlockingStatsRepository`）で代替している。

## StrictMode（デバッグビルドのみ・処方側の実演）

ANR を仕込む demoMode とは**独立**して、デバッグビルドでは常にメインスレッドのディスク I/O を検出する（`core/debug/StrictModeInstaller.kt`）。違反が出ると画面上部にバナーが出て、タップすると Android が出力したスタックトレース全文を表示する。マーカー `[ANR-xx]` は付けない（仕込み側ではなく気づく側のため）。

- **ネットワークは OS が既定でメインスレッド禁止**（`initThreadDefaults` が `detectNetwork` + `penaltyDeathOnNetwork` を入れる＝`NetworkOnMainThreadException` の正体）。**ディスク I/O は検出すらされない**ので、自分でスイッチを入れる必要がある。
- 既定ポリシーを引き継ぐため `ThreadPolicy.Builder(StrictMode.getThreadPolicy())` から組み立てる。`Builder()` を新規に作ると `penaltyDeathOnNetwork` が消え、デバッグビルドの方が緩くなる。
- `penaltyDeath` は使わない。落とさずに気づかせるのが目的。
- リリースビルドでは `BuildConfig.DEBUG` で丸ごと無効。

## 技術スタック

Kotlin / Jetpack Compose (Material3) / Koin（DI）/ Room + DataStore（SQLDelight は AGP9 対応待ちで無効化中）/ WorkManager / Firebase Crashlytics。Version Catalog 管理・単一モジュール。

## ビルド・実行

```bash
./gradlew :app:assembleDebug
# または Android Studio で開いて Run
```

## パッケージ構成

```
com.pelantica.dorodorotimer
├── app/            Application（Koin 起動）/ startup/（ANR-02 の SDK風初期化と StartupGate）
├── di/             Koin モジュール（demoMode 差し替え／lazyModule 処方の場）
├── core/debug/     DemoConfig（ANR再現モードのフラグ）/ StrictMode バナー
├── core/ui/        Theme / SectionCard
├── feature/timer/  タイマー画面（ポモドーロ本体・時間ホイール）
├── feature/stats/  統計画面（日別集計・デモ用シードの2セクション表示）
├── feature/settings/ 設定画面（demoMode トグル・開発ツール）
├── data/local/     Room / DataStore / 生SQLite（stats。SQLDelight は無効化中）
├── domain/model/   PomodoroPreset / TimerPhase
└── service/        AmbientSoundService（雨音FGS）/ TimerAlarmReceiver / work/（ANRログ送信Work）
```

## ステータス

タイマー・統計・設定・テーマは製品品質で動作。ANR パターンは **01 / 02 / 03 / 05 / 06 が実装済み**（対応表参照。02 は正版=ワーカー実行込み、03 / 05 は実機E2E検証済み）。04 / 07 / FGS は未着手。
