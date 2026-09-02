package com.pelantica.dorodorotimer.domain.repository

/**
 * 完了した集中セッション1回分を記録する。読み取り（日別集計）は [StatsRepository]。
 * [StatsRepository]（ANR-01 の DI 差し替え点）とは意図的に分離し、記録には常に安全な実装だけを配線する。
 */
interface FocusSessionRecorder {
    /** @param completedAtEpochMs 実際に0へ到達した時刻。日別集計の日付はこの値から決まる。 */
    suspend fun record(durationSeconds: Int, completedAtEpochMs: Long)
}
