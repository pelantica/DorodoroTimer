package com.pelantica.dorodorotimer.service.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters

/**
 * ANR/クラッシュのテレメトリを送信するバックグラウンドワーカー。
 *
 * [ANR-05] doWork 自体は無実＝ワーカースレッドで軽量に動く。真犯人は Application.onCreate
 *  （ANR-02）: 冷えたプロセスに WorkManager がジョブを投げると新プロセスが立ち上がり、
 *  その onCreate の重い初期化が起動 ANR になる。
 *  処方: doWork は軽量に保ち、ANR-02 を直して連結を断つ。再現手順は README を参照。
 */
class AnrLogUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // [ANR-05] ここは軽量に BG スレッドで実行される＝無罪（真犯人はクラス KDoc 参照）。
        AnrLogUploader.upload(dummyReports())
        return Result.success()
    }

    /** デモ用の固定ダミーレポート。実アプリでは端末に蓄積された未送信レポートに相当。 */
    private fun dummyReports(): List<String> = listOf(
        "ANR: Input dispatching timed out (Application.onCreate)",
        "ANR: Broadcast of Intent timed out",
    )
}
