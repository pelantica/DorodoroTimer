package com.tefumichangdev.dorodorotimer.service.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters

/**
 * ANR/クラッシュのテレメトリを送信するバックグラウンドワーカー。
 *
 * [ANR-05] doWork 自体は無実 = 軽量に保つ。ANRログ送信は WorkManager の
 *  ワーカースレッドで正しく非同期に行われるため、これがメインを固めることはない。
 *  起動 ANR の真犯人は Application.onCreate（ANR-02）。冷えたプロセス（アプリが
 *  BG で終了した状態）に WorkManager がジョブを投げると新プロセスが立ち上がり、
 *  その onCreate で ANR-02 の重い初期化が起動枠（約20秒）内で走ることで起動ANRになる。
 *
 *  皮肉: WorkManager の定番用途である「ログ送信キュー」自体は完全に正しく動く。
 *  にもかかわらず、そのジョブがプロセスを起こすことで起動ANRを誘発する。
 *
 *  連結シナリオ: ANR-02 & ANR-05 の両方を ON にしてアプリを BG に落とし（必要なら
 *  プロセスを kill し）、しばらく待つと WorkManager がジョブを起動 → 冷たい起動 →
 *  ANR-02 の重い onCreate → ANR。
 *
 *  処方: doWork は正しく軽量に保ち（本クラスはその模範）、ANR-02 を直して連結を断つ。
 */
class AnrLogUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // [ANR-05] ANRログ送信は正しく軽量に BG スレッドで実行される＝無罪。
        //  真犯人はこのメソッドではなく、このジョブが冷えたプロセスを起こすことで
        //  Application.onCreate（ANR-02）が起動枠内で走ること。
        AnrLogUploader.upload(dummyReports())
        return Result.success()
    }

    /** デモ用の固定ダミーレポート。実アプリでは端末に蓄積された未送信レポートに相当。 */
    private fun dummyReports(): List<String> = listOf(
        "ANR: Input dispatching timed out (Application.onCreate)",
        "ANR: Broadcast of Intent timed out",
    )
}
