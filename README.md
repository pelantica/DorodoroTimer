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
| ANR-01 | メインスレッド I/O（生SQLite vs Room） | busy | input | `di/AppModule.kt:76`（DI差し替え）/ `data/local/stats/BlockingStatsRepository.kt:20` | Room の suspend DAO に任せる（守ってくれないライブラリは自前で `withContext(IO)`） | 実装済み |
| ANR-02 | Application.onCreate の重い初期化 | busy | 起動 | `app/DorodoroApplication.kt:27` / `app/startup/StartupGate.kt:58` | `StartupGate.runOnWorkerThread`（onCreate は予約だけ）。Koin `lazyModule` も候補 | 実装済み（正版込み） |
| ANR-03 | Deeplink 起動 × ロック競合 | waiting | input | `feature/stats/StatsScreen.kt:42`（フックのみ） | シングルトン遅延評価 / 重い処理をメイン外へ | フックのみ（本体未実装） |
| ANR-04 | Keystore 操作（Binder + セキュアHW IPC） | waiting | binder | _未_ | 鍵操作を IO へ | 未着手（速射枠） |
| ANR-05 | WorkManager / JobService（ANR-02 連結） | waiting | job | `service/work/AnrLogUploadScheduler.kt:18` / `AnrLogUploadWorker.kt:26` | doWork 自体は正しく軽量（無罪）。真犯人は起こされた先の重い onCreate | 実装済み |
| ANR-06 | BroadcastReceiver（onReceive 重処理） | busy/waiting | broadcast | `service/TimerAlarmReceiver.kt:22` / `service/ReceiverWork.kt:26` | `goAsync()` / 処理をメイン外へ | 実装済み（実機5秒超の最終校正は登壇前TODO） |
| ANR-07 | DexFile / ClassLoader（起動時集中） | busy/waiting | 起動 | _未_ | Koin `lazyModule` で遅延 | 未着手（速射枠） |
| ANR-FGS | ForegroundService の startForeground 5秒ルール | waiting | service | _未_（実体候補は `service/AmbientSoundService.kt`。現状 ANR フック無し） | 即 startForeground / 重い初期化を後へ | 未着手（CFP外・目玉候補） |

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

タイマー・統計・設定・テーマは製品品質で動作。ANR パターンは **01 / 02 / 05 / 06 が実装済み**（対応表参照。02 は正版=ワーカー実行込み）。03 はフックのみ、04 / 07 / FGS は未着手。
