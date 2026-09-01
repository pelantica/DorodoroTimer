package com.pelantica.dorodorotimer.data.local.stats

import com.pelantica.dorodorotimer.domain.model.TimerPhase

/**
 * [ANR-01] デモ用シードデータの共通仕様。
 *
 * [BlockingStatsRepository]（生SQLite・withContext なし・メインで同期実行＝ANRする）と
 * [OffloadedStatsRepository]（Room suspend DAO・withContext(IO)＝ANRしない）が
 * **同じ量・同じ内容のデータを、同じやり方（1件ずつ非トランザクションで書き込む）** で投入するための
 * 唯一の生成ロジック。
 *
 * 「どちらも同じ作業をしているのに、片方はメインを固めてANRし、もう片方はUIが生きたまま
 * 完了する」を見せるには、生成するデータと総作業量が両実装で完全に一致している必要がある。
 * 実際の書き込み（トランザクションで囲むか・どのスレッドで実行するか）は呼び出し元
 * （[RawSqliteStatsHelper.reseedForDemo] / [OffloadedStatsRepository]）の責務。
 */
object DemoStatsSeed {

    /** シード1行分のデータ。 */
    data class Row(
        val phase: String,
        val durationSeconds: Int,
        val completedAtEpochMs: Long,
    )

    /**
     * [rowCount] 件のシード行を決定的に生成する。
     *
     * @param nowEpochMs 基準時刻。呼び出し元が `System.currentTimeMillis()` 等を渡す。
     *   テストでは固定値を渡すことで生成結果を完全に再現できる（同じ入力なら同じ行が出る）。
     * @param rowCount 生成行数。5件に1件が BREAK（25分集中+5分休憩のポモドーロ比を模す）で、
     *   completedAtEpochMs は過去 [SEED_SPAN_DAYS] 日にばらけさせる（日別集計が意味を持つように）。
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

    /**
     * デモ用シード行数の既定値。1件ごとに非トランザクションの書き込み（fsync 相当）が走るため、
     * 所要時間はほぼ行数に比例する。
     *
     * [BlockingStatsRepository] 側の実測（エミュ API 37）では 3000行が約4.2秒で
     * 入力ディスパッチの5秒しきい値に届かず**ANRしない**のに対し、5000行は約7秒で安定して超える。
     * 端末が変われば再校正する（この定数だけ変えればよい）。TODO(登壇前): 本番端末で実測。
     */
    const val SEED_ROW_COUNT = 5000

    /** シードした completedAtEpochMs を分散させる日数幅。 */
    private const val SEED_SPAN_DAYS = 14
}
