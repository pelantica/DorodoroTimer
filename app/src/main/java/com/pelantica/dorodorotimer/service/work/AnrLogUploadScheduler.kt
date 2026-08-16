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
 * ## ポリシー選定で3度ハマった記録（結論: 前面起動限定の [ExistingWorkPolicy.REPLACE]）
 *
 * 「冷えたプロセスを20秒後に起こす目覚ましが、**常にちょうど1つ**ある」状態を作りたいだけなのに、
 * `ExistingWorkPolicy` の選択だけでは解けなかった。3回の実測での失敗を残しておく。
 *
 * **1度目・REPLACE（無条件に enqueue）→ 自爆レース。**
 * 起こされたプロセスの `onCreate` から REPLACE で enqueue すると、WorkManager は同じ一意名の
 * 既存 Work＝**自分を起こしてくれた当の Work をキャンセル**する。`enqueue` の実処理は
 * task executor（別スレッド）で進むのに `SystemJobService.onStartJob` はメインの順番待ちなので、
 * `onStartJob` が呼ばれる前にキャンセルが刺さり `doWork` がほとんど走らない。
 *
 * **2度目・KEEP → 取りこぼし。**
 * 20秒の期限がアプリ生存中に切れた（または期限切れ Work が次の起動時に復旧された）場合、
 * GreedyScheduler がその Work を**その場でプロセス内実行**して消化する。ちょうどその瞬間に
 * `onCreate` の enqueue が走ると、KEEP は「実行中の Work がある」と見て**何もしない**。
 * 消化後には何も残らず、次に kill しても誰も起こしに来ない。
 *
 * **3度目・APPEND_OR_REPLACE → 目覚ましが遠のく。**
 * 実行中でも後ろに積めるので取りこぼしは消えるが、積んだ Work の初期遅延20秒は
 * **前の Work が完了してから**数え始める。消化されないまま冷起動を重ねるとチェーンが伸び、
 * n 個目の目覚ましは n×20秒後になる。数サイクルで「待っても起きない」に見える状態になり、
 * 一度伸びたチェーンは前面起動を繰り返しても短くならない（積むだけなので）。
 *
 * ## 結論: ポリシーではなく「**いつ武装するか**」を絞る
 *
 * 3つの失敗はどれも「**起こされた側が自分の目覚ましを触る**」ことが原因だった。
 * ならばポリシーを捻るのではなく、[com.pelantica.dorodorotimer.app.startup.StartupOrigin]
 * で**前面起動のときだけ武装する**と決めればよい（呼び出し側の分岐を参照）。こうすると:
 *
 * - 自分を起こした Work を触ることが構造的に起こりえない → 自爆レースが消える（1度目の解決）。
 * - REPLACE は「既存があってもなくても必ず1つ書く」ので取りこぼしが無い（2度目の解決）。
 * - 毎回置き換えるのでチェーンが伸びない＝目覚ましは常に**20秒後ちょうど1つ**（3度目の解決）。
 *   詰まったチェーンから始めても、次の前面起動1回で必ず正常化する（復旧手順が要らない）。
 *
 * 「前面起動のたびにカウントダウンが振り出しに戻る」のは、この用途ではむしろ正しい:
 * デモの手順は「アプリを開く → 落として殺す → 待つ」であり、起点は常に直前の前面起動だから。
 * 実アプリでも「未送信ログの送信予約は、アプリが使われたときに入れ直す」は素直な設計になる。
 *
 * ## 「鳴らない」の原因は Work ポリシーとは限らない（端末側の事情・2026-08-17 実測）
 *
 * ポリシーを直した後も再現に失敗することがあり、原因は WorkManager の外だった。
 * `dumpsys jobscheduler` でこのジョブの `Unsatisfied constraints` を見れば一発で分かる
 * （名前空間付きで登録されるため、素の `JOB #` では grep に掛からない点に注意:
 * `JOB androidx.work.systemjobscheduler:u0aNNN/NN: ... #AnrLogUploadWorker#@...`）。
 *
 * - **`WITHIN_QUOTA` が外れる**: 非給電の端末では QuotaController が働き、短時間にジョブを
 *   何度も走らせると保留のまま鳴らなくなる（実測では25分で24セッション消化して枯渇）。
 *   充電中はクォータが免除されるので、給電すれば解消する。
 *   `cmd jobscheduler run -f` の `-f` は接続性などの技術的制約を無視するだけで、
 *   **クォータは無視しない**（枯渇状態では `run -f` も空振りする）。
 * - **`FLEXIBILITY` によるまとめ実行**: Android 15+ は急ぎでないジョブをバッチ処理するため、
 *   初期遅延が切れてから実際に鳴るまで更に時間がかかる（実測: kill から約102秒後に自然発火）。
 *   仕掛けは正しく動いているので、待てば必ず鳴る。
 *
 * どちらもデモの待ち時間の問題であって ANR の再現性の問題ではない。
 * `scripts/demo-anr05.sh` は給電状態の確保・フレックス無効化・遅延満了後の強制実行で吸収する。
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
        // [ANR-05] 20秒の初期遅延で「BG退避 / プロセスkill → 冷えた起動」の猶予を作る。
        //  遅延ゼロだとアプリ生存中に即実行され、連結シナリオが再現しない。
        //  逆に前面のまま20秒放置すると、その場で消化されて目覚ましが消える（それも正しい挙動）。
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
