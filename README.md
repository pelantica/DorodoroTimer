# Dorodoro Timer 🍅⏱

DroidKaigi 2026 セッション **「あなたのANRはどこから？ — 発生する仕組みを診断し、症状別に処方する」** の登壇用サンプルアプリ。

「集中作業用タイマー（ポモドーロ）」という**普通のアプリ**のコードに、ANR の種になる実装を**自然な形で埋め込み**、各所のマーカーコメント（`// [ANR-xx] ...`）と、この README の対応表でスライドの事例に紐づける。デモ用の不自然な発火ボタンは作らず、「**普通に操作すると固まる**」を見せる。

> 将来は「25分の縛りが鬱陶しい」を改善する方向で機能追加してリリース予定。DroidKaigi 後に KMP 化も視野。

設計の詳細・決定の経緯は Notion: **ANRサンプルアプリ（DorodoroTimer）設計メモ** を参照（真実の源）。

## demoMode（教材と製品の両立）

標準コードは**正しい（リリース品質）実装**。`DemoConfig.enabled`（設定画面のトグル）が **ON のときだけ** ANR 誘発経路を通る。リリースは常に OFF。

- **分岐は原則 DI（Koin）で実装ごと差し替え**（呼び出し側はクリーン、before/after が実装ファイル単位で並ぶ）。
- DI で差し替えにくい局所だけ、その場に `if (DemoConfig.enabled) { /* [ANR-xx] */ }`。

## スライド事例 ↔ コード 対応表

> 骨格段階のため配置（file:line）は未確定。各パターン実装時に埋める。軸: busy=作業中 / waiting=待たされ。

| ANR-ID | 事例 | 軸 | 締切種別 | 配置 | 処方 | 状態 |
| --- | --- | --- | --- | --- | --- | --- |
| ANR-01 | メインスレッド I/O（Room vs SQLDelight） | busy | input | _未_ | `withContext(IO)` / Room の suspend DAO / SQLDelight は自前で offload | 未着手 |
| ANR-02 | Application.onCreate の重い初期化 | busy | 起動 | _未_ | Koin `lazyModule` / 初期化の遅延・取捨選択 | 未着手 |
| ANR-03 | Deeplink 起動 × ロック競合 | waiting | input | _未_ | シングルトン遅延評価 / 重い処理をメイン外へ | 未着手 |
| ANR-04 | Keystore 操作（Binder + セキュアHW IPC） | waiting | binder | _未_ | 鍵操作を IO へ | 未着手（速射枠） |
| ANR-05 | WorkManager / JobService | waiting | job | _未_ | doWork を正しく実装 | 未着手 |
| ANR-06 | BroadcastReceiver（再起動でリマインダ再設定 等） | busy/waiting | broadcast | _未_ | `goAsync()` / 処理をメイン外へ | 未着手 |
| ANR-07 | DexFile / ClassLoader（起動時集中） | busy/waiting | 起動 | _未_ | Koin `lazyModule` で遅延 | 未着手（速射枠） |
| ANR-FGS | ForegroundService の startForeground 5秒ルール | waiting | service | `service/TimerForegroundService.kt` | 即 startForeground / 重い初期化を後へ | 未着手（CFP外・目玉候補） |

CFP 外の追加候補（重い同期計算 / Compose 再コンポーズ / ContentProvider 隠れ初期化 / 同期 Binder / wait-notify / commit() / Bitmap decode / 接続プール枯渇 / nativePollOnce の罠 等）は Notion のバックログに記録済み。採否は後日選定。

## 永続化（事例①の核：ライブラリのスレッド管理を見極める）

| ライブラリ | スレッドの扱い | 役割 |
| --- | --- | --- |
| Room | suspend/Flow DAO は IO へ逃す（守ってくれる側） | 「守ってくれる」例 |
| SQLDelight | クエリはデフォルト同期実行（守ってくれない側） | 「守ってくれない」例＝事例①の正体 |
| DataStore | suspend/Flow で非同期 | 補助 |

## 技術スタック

Kotlin / Jetpack Compose (Material3) / Koin（DI）/ Room + SQLDelight + DataStore / WorkManager / Navigation3。Version Catalog 管理・単一モジュール。

## ビルド・実行

```bash
./gradlew :app:assembleDebug
# または Android Studio で開いて Run
```

## パッケージ構成

```
com.tefumichangdev.dorodorotimer
├── app/            Application（Koin 起動）
├── di/             Koin モジュール（demoMode 差し替え／lazyModule 処方の場）
├── core/debug/     DemoConfig（ANR再現モードのフラグ）
├── core/ui/        Theme
├── feature/timer/  タイマー画面（最小の動くポモドーロ）
├── feature/stats/  統計画面（プレースホルダ）
├── feature/settings/ 設定画面（demoMode トグル）
├── data/local/     Room / SQLDelight / DataStore
├── domain/model/   PomodoroPreset / TimerPhase
└── service/        TimerForegroundService（器のみ）
```

## ステータス

🚧 **骨格のみ**。動く最小ポモドーロ（25分集中＋5分休憩、start/pause/reset）と、各ANRパターンを後から差し込むための器・依存・demoMode 土台を用意した段階。ANR パターン本体は未実装。
