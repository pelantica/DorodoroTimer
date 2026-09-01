# CLAUDE.md

このファイルは、リポジトリ作業時に Claude Code が参照する規約です。

## プロジェクト概要

**DorodoroTimer**：集中作業用タイマー（ポモドーロ）。Kotlin + Jetpack Compose + Koin + Room。

二重の位置づけを持つ：
1. **DroidKaigi 2026 ANRセッション「あなたのANRはどこから？」の登壇用サンプルアプリ** — 普通のアプリのコードに ANR の種を自然に埋め込み、マーカーコメント `// [ANR-xx]` と README でスライド事例に紐づける。
2. **将来リリースする製品の種** — 「25分の縛りが鬱陶しい」を改善する方向で育てる。DroidKaigi 後に KMP 化も視野。

### 最初に読むもの（真実の源）
- `README.md` — スライド事例 ↔ コードの対応表、demoMode 方針、永続化方針
- 設計メモ・事例のバックログ（リポジトリ外のドキュメントツールで管理）

### 情報の置き場（DroidKaigi 2026 全体の地図・2026-07-14 交通整理）
- アプリのコード・ANR マーカー対応表 → **この repo**（README.md）
- スライド本文・台本・事例検討・構成の決定 → 隣の `../droidkaigi2026-anr-slides`（`anr_session.md` / `cases.md` / `NOTES.md`）
- 登壇固有の知識（精読メモ・調整事項） → リポジトリ外のドキュメントツール
- 一般化できる技術知識 → KB `~/knowledge/dev/*.md`
- タスク → リポジトリ外のタスク管理
- ⚠️ スライド構成の正本は slides repo の `NOTES.md`。他所に構成メモを作らない

## このアプリ固有の超重要ルール

### demoMode（教材と製品の両立）
- 標準コードは**正しい（リリース品質）実装**にする。マスタートグル＋ ANR ごとの個別トグル（設定画面、`DemoConfig.isOn(Anr.XX)`）が **ON のときだけ** ANR 誘発経路を通る。リリースは常に OFF。
- **分岐の入れ方は「ANR の原因の粒度」に合わせる**（目的は呼び出し側に分岐を撒かないこと。DI かローカル `if` かは手段であって原則ではない）。
  - 原因が**実装まるごとの性質**（例: DB ライブラリがスレッドを管理するか否か＝事例①）なら → **DI（Koin）で実装ごと差し替える**。正版と ANR 版の2実装を用意し、demoMode でどちらを注入するか切替。2実装の差分そのものがレッスンになる。
  - 原因が**クラス内の局所操作**（`commit()`/`apply()`、`onReceive` 内、`Application.onCreate` の初期化順序、`startForeground` の有無 等）なら → その場に `if (DemoConfig.isOn(Anr.XX)) { /* [ANR-xx] */ }`。共有クラスを丸ごと複製して1行だけ変えるより、ANR 箇所がマーカー数行で一目で分かる。
  - 迷ったら「**ANR するコードとしないコードの差分が最小・最明瞭になる方**」を選ぶ。
- **ANR を仕込むコードには必ず `// [ANR-xx] ...` マーカーコメントを付け、README の対応表に1行追加する**（file:line・処方・スライド#）。

### 永続化の使い分け（事例①の核）
- Room = suspend/Flow DAO が IO へ逃す「守ってくれる」側。
- SQLDelight = クエリがデフォルト同期実行の「守ってくれない」側（事例①の正体）。
- ⚠️ **SQLDelight は現在無効化中**（2.1.0 が AGP 9.1.0 未対応＝`BaseExtension` 参照エラー）。`.sq` と TODO は残してある。再有効化は ANR-01 実装時 or AGP9 対応版が出てから。`app/build.gradle.kts` と `build.gradle.kts` のコメント参照。

## ビルド・実行

- **Android Studio で開いてビルドするのが安全**（同梱 JBR=JDK 21 を使う）。
- CLI で叩くなら `JAVA_HOME` を JDK 21 に向けること。既定の JDK 26 だと `assembleDebug` が `jlink`/JdkImageTransform で落ちる（コードの問題ではなく環境）。
  ```bash
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
  ```
- 依存は `gradle/libs.versions.toml`（Version Catalog）で一元管理。新規ライブラリ追加は目的と選定理由を相談してから。

## コード規約

### 文字列・色・寸法
- ユーザーに見える文字列は **必ず `strings.xml`** に定義し `stringResource(R.string.xxx)` で参照。ハードコード可なのは Preview ダミー値・ログ・例外メッセージのみ。キーは `snake_case`、画面/機能をプレフィックスに。
- 色は `MaterialTheme.colorScheme.xxx` を優先。Composable 内で `Color(0xFF...)` 直書きは避ける。
- 寸法は単発は直書き可、複数箇所で使う値は Kotlin 定数に。

### 命名・Compose
- クラス/Composable/ファイル：PascalCase、関数/変数：camelCase、Boolean：`is`/`has`/`should`。
- State：`XxxUiState` / ViewModel：`XxxViewModel` / Event：`XxxEvent`。
- 画面ルートは `XxxScreen`（ViewModel を受け取り、描画は `XxxContent` に委譲して Preview しやすく）。`Modifier` は第1引数・デフォルト `Modifier`。
- **画面ごとに1つの ViewModel**。ViewModel は対応する画面と同じパッケージに置く。

### パッケージ構成
ルート：`com.pelantica.dorodorotimer`
- `app`（Application・Koin 起動）/ `di`（Koin モジュール）/ `core/debug`（DemoConfig）/ `core/ui`（Theme）
- `feature/timer|stats|settings`（画面単位）/ `data/local`（Room・SQLDelight・DataStore）/ `domain/model` / `service`

## 作業の進め方
- 実装着手前に **関連コードを読み、変更計画（対象ファイル・概要・設計判断の選択肢）を提示してユーザーの承認を得る**。
- 設計判断が要る場面（新規ライブラリ、Room スキーマ破壊的変更、DI/アーキ変更）は選択肢を提示して相談。
- main に直接コミットしない。作業ブランチを切る。
- 仕様の唯一の源はリポジトリ外の設計メモ。曖昧な箇所は勝手に埋めず質問する。
- **設計ドキュメントはリポジトリ外で管理**する。git には置かない。実装計画（plan）は scratchpad（使い捨て）。
- 段階的な実装（ANRパターン等）は **subagent-driven** で進める（サブエージェント活用・コミット確認などの一般方針は `~/.claude/CLAUDE.md`）。

## コミット
- メッセージは日本語OK。Conventional Commits 風（`feat:`/`fix:`/`refactor:`/`chore:`/`docs:`）。1コミット1粒。
