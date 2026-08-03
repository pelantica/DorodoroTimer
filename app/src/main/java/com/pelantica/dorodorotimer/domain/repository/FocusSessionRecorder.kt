package com.pelantica.dorodorotimer.domain.repository

/**
 * 完了した集中セッション1回分を記録する。読み取り（日別集計）は [StatsRepository]。
 *
 * [StatsRepository] に書き込みメソッドを足さず別インターフェースにしているのは意図的。
 * StatsRepository は ANR-01 の DI 差し替え点（Blocking ⇔ Offloaded）で、
 * 書き込みまで載せると「読み取り集計がメインを塞ぐ」という事例①の対比が濁る。
 * 記録は事例と無関係の製品機能なので、常に安全な実装だけを配線する。
 */
interface FocusSessionRecorder {
    /** @param completedAtEpochMs 実際に0へ到達した時刻。日別集計の日付はこの値から決まる。 */
    suspend fun record(durationSeconds: Int, completedAtEpochMs: Long)
}
