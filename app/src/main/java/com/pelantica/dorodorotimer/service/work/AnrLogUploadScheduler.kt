package com.pelantica.dorodorotimer.service.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * [AnrLogUploadWorker] のスケジューリングユーティリティ。
 *
 * demoMode（ANR-05）が ON のときのみ DorodoroApplication.onCreate から呼ばれる。
 * OFF（リリース既定）では呼ばれないため enqueue は発生しない。
 *
 * **前面起動のときだけ**呼ぶこと（呼び出し側が
 * [com.pelantica.dorodorotimer.app.startup.StartupOrigin] で絞っている）。
 * 起こされた側が同じ一意名の Work を触ると、`ExistingWorkPolicy` のどれを選んでも
 * 「自分を起こした目覚ましを壊す（REPLACE）／取りこぼす（KEEP）／先送りにする
 * （APPEND_OR_REPLACE）」のいずれかを踏む——3ポリシーとも実測で確認済み。
 * 前面限定なら REPLACE で「目覚ましは常に [INITIAL_DELAY_SECONDS] 秒後にちょうど1つ」に揃い、
 * どんな詰まり方をしていても次の前面起動1回で自己回復する。
 * 実アプリでも「未送信ログの送信予約は、アプリが使われたときに入れ直す」は素直な設計。
 *
 * 鳴らないときは Work ポリシーより先に端末側の事情を疑う（`dumpsys jobscheduler` で
 * このジョブの `Unsatisfied constraints` を確認。名前空間付き
 * `JOB androidx.work.systemjobscheduler:...#AnrLogUploadWorker#` で探すこと）:
 * 非給電だとクォータ（`WITHIN_QUOTA`）で保留され、Android 15+ はフレックスの
 * まとめ実行で遅延満了後さらに1〜2分待たされる。`scripts/demo-anr05.sh` は両方吸収する。
 * （ポリシー3連敗の経緯・実測の詳細はスライド repo の NOTES.md）
 */
object AnrLogUploadScheduler {
    private const val UNIQUE_WORK_NAME = "anr_log_upload"

    /**
     * [ANR-05] 武装から目覚ましが鳴るまでの猶予。デモ手順「アプリを開く → HOME → kill」を
     * 落ち着いて実行できる長さが要る。短くすると前面のまま消化されやすくなり
     * （前面で遅延が切れると GreedyScheduler がその場で消化して目覚ましが消える）、
     * 長くするとデモの待ち時間が増える。
     *
     * 実測（2026-08-17・エミュ API 37）: `am kill` は対象が cached に落ちるまで no-op なので、
     * HOME を送ってから実際に殺せるまで **10〜15秒**かかる。20秒だと余裕が5秒しかなく
     * 「殺し終える前に遅延が切れて前面で消化される」ことがあったため 30 秒に広げた。
     * 変更したら `scripts/demo-anr05.sh` の `ARM_DELAY_SECONDS` も合わせること。
     */
    internal const val INITIAL_DELAY_SECONDS = 30L

    /**
     * [ANR-05] 目覚ましを1つだけ張り直す。**前面起動のときだけ**呼ぶこと
     * （背面起動から呼ぶと自分を起こした Work を消す。理由はこのクラスの KDoc）。
     */
    fun enqueue(context: Context) {
        // [ANR-05] 初期遅延で「BG退避 / プロセスkill → 冷えた起動」の猶予を作る。
        //  遅延ゼロだとアプリ生存中に即実行され、連結シナリオが再現しない。
        //  逆に前面のまま遅延が切れると、その場で消化されて目覚ましが消える（それも正しい挙動）。
        val req = OneTimeWorkRequestBuilder<AnrLogUploadWorker>()
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            // [ANR-05] 前面起動限定なので REPLACE で安全＝毎回きっかり1つに揃う。
            //  KEEP / APPEND_OR_REPLACE で起きた失敗はこのクラスの KDoc に記録した。
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }
}
