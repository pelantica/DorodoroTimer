package com.tefumichangdev.dorodorotimer.service.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters

/**
 * 統計データを同期するバックグラウンドワーカー。
 *
 * [ANR-05] doWork 自体は無実 = 軽量に保つ。WorkManager がプロセスを BG で起こすだけ。
 *  起動 ANR の真犯人は Application.onCreate（ANR-02）。冷えたプロセス（= アプリが
 *  バックグラウンドで終了した状態）に WorkManager がジョブを投げると新プロセスが立ち上がり、
 *  ANR-02 の重い onCreate が起動枠（約 20 秒）内で走ることで起動 ANR になる。
 *
 *  連結シナリオ: demoMode で ANR-02 & ANR-05 の両方を ON にして、アプリを BG に落とし、
 *  しばらく待つと WorkManager がジョブを起動 → 冷たい起動 → ANR-02 の重い onCreate → ANR。
 *
 *  処方: doWork は正しく軽量に保ち（本クラスはその模範実装）、ANR-02（onCreate の
 *  重い eager 初期化）を直すことで連結を断ち切る。
 */
class StatsSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // [ANR-05] doWork はここでは何もしない（将来の実装プレースホルダ）。
        //  ANR の原因はこのメソッドではなく、このジョブがプロセスを起こすことで
        //  Application.onCreate（ANR-02）が起動枠内で走ることにある。
        return Result.success()
    }
}
