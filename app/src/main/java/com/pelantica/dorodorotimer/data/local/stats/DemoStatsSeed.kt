package com.pelantica.dorodorotimer.data.local.stats

import com.pelantica.dorodorotimer.domain.model.TimerPhase

/**
 * [ANR-01] デモ用シードデータの共通仕様。
 * [BlockingStatsRepository]（ANRする側）と [OffloadedStatsRepository]（しない側）が
 * 同じ量・同じ内容のデータを投入するための唯一の生成ロジック。
 * 総作業量を両実装で一致させ、対比を「スレッド管理の有無」だけに絞るためにある。
 */
object DemoStatsSeed {

    /** シード1行分のデータ。 */
    data class Row(
        val phase: String,
        val durationSeconds: Int,
        val completedAtEpochMs: Long,
    )

    /**
     * [rowCount] 件のシード行を決定的に生成する（同じ入力なら同じ行が出る）。
     * 5件に1件が BREAK、completedAtEpochMs は過去 [SEED_SPAN_DAYS] 日にばらけさせる。
     *
     * @param nowEpochMs 基準時刻。テストでは固定値を渡して結果を再現できる。
     */
    fun generate(rowCount: Int, nowEpochMs: Long): List<Row> = buildList(rowCount) {
        for (i in 0 until rowCount) {
            val dayOffset = (i % SEED_SPAN_DAYS).toLong()
            val withinDayOffsetMs = (i % 86_400).toLong() * 1000L
            val completedAt = nowEpochMs - dayOffset * 86_400_000L - withinDayOffsetMs
            val isBreak = i % 5 == 4
            val phase = if (isBreak) TimerPhase.BREAK.name else TimerPhase.FOCUS.name
            val durationSeconds = if (isBreak) 300 else 1500
            add(Row(phase = phase, durationSeconds = durationSeconds, completedAtEpochMs = completedAt))
        }
    }

    /** デモ用シード行数の既定値。所要時間はほぼ行数に比例するため、負荷の調整はこの定数だけでよい。 */
    const val SEED_ROW_COUNT = 5000

    /** シードした completedAtEpochMs を分散させる日数幅。 */
    private const val SEED_SPAN_DAYS = 14
}
