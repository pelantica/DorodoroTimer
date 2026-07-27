package com.tefumichangdev.dorodorotimer.data.local.stats

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.tefumichangdev.dorodorotimer.domain.model.DailyStat
import com.tefumichangdev.dorodorotimer.domain.model.TimerPhase

/**
 * 生SQLite（SQLiteOpenHelper）を使った統計ヘルパー。
 * SQLiteOpenHelper のクエリは呼んだスレッドで同期実行される＝「守ってくれない」側。
 * Room の suspend DAO（「守ってくれる」側）との対比が事例①の核。
 */
class RawSqliteStatsHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phase TEXT NOT NULL,
                durationSeconds INTEGER NOT NULL,
                completedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // デモ用DBなので破壊的でよい。旧テーブル名（focus_sessions、Room側と紛らわしいため改名した）
        // が残っていれば併せて掃除する。
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $LEGACY_TABLE_NAME")
        onCreate(db)
    }

    /** 単発 INSERT のプリミティブ。[reseedForDemo] が非トランザクションでループ呼び出しする。 */
    fun insertBlocking(phase: String, durationSeconds: Int, completedAtEpochMs: Long) {
        writableDatabase.insert(TABLE_NAME, null, ContentValues().apply {
            put("phase", phase)
            put("durationSeconds", durationSeconds)
            put("completedAtEpochMs", completedAtEpochMs)
        })
    }

    /**
     * [ANR-01] デモ用シード: 既存行を全削除してから [rowCount] 件を1件ずつ INSERT し直す。
     *
     * わざと `beginTransaction()`/`endTransaction()` で囲まない。SQLite は明示トランザクション外の
     * INSERT を「1文＝1トランザクション」として都度コミット（＝都度 fsync）するため、
     * このループは `Thread.sleep` のような偽の重りではなく、
     * 「トランザクションで囲み忘れた INSERT ループ」という実務で実在する同期I/Oの重さそのものを再現する。
     *
     * [BlockingStatsRepository.dailyStats]（Main起点、withContext なし）から呼ばれるため、
     * この重い非トランザクションI/Oがそのままメインスレッドを専有し ANR を誘発する。
     *
     * **毎回入れ直すのは登壇デモの再現性のため**。「空のときだけ」にすると初回しか ANR せず、
     * しかも ANR でプロセスが落ちると中途半端な行数が残って以後まったく再現しなくなる
     * （実機で確認済み: 2166行が残り、集計のみ 8ms で完了して不発になった）。
     * 毎回リセットすることで、統計タブを開くたびに同じ所要時間で確実に再現できる。
     *
     * @param rowCount シード行数。実機ANR計測で校正するためデフォルト引数化してある
     *   （[SEED_ROW_COUNT] を変えるだけで負荷を調整できる）。テストは小さい値でロジックのみ検証する。
     */
    fun reseedForDemo(rowCount: Int = SEED_ROW_COUNT) {
        val db = writableDatabase
        // 前回のデモデータを消す（DELETE 自体は軽い。重いのは下の非トランザクションINSERTループ）。
        db.delete(TABLE_NAME, null, null)

        val start = System.currentTimeMillis()
        for (i in 0 until rowCount) {
            // completedAtEpochMs を過去 SEED_SPAN_DAYS 日にばらけさせる（日別集計が意味を持つように）。
            val dayOffset = (i % SEED_SPAN_DAYS).toLong()
            val withinDayOffsetMs = (i % 86_400).toLong() * 1000L
            val completedAt = start - dayOffset * 86_400_000L - withinDayOffsetMs
            // [ANR-01] 5件に1件が休憩（25分集中+5分休憩のポモドーロ比を模す）
            val isBreak = i % 5 == 4
            val phase = if (isBreak) TimerPhase.BREAK.name else TimerPhase.FOCUS.name
            val durationSeconds = if (isBreak) 300 else 1500
            // [ANR-01] トランザクションで囲まない: 1件ごとにコミット＝1件ごとにfsync（本物の遅いI/O）
            insertBlocking(phase, durationSeconds, completedAt)
        }
        val elapsed = System.currentTimeMillis() - start
        Log.d(TAG, "reseedForDemo: inserted $rowCount rows (non-transactional) in ${elapsed}ms")
    }

    /**
     * 全件取得→Kotlin側でFOCUSのみ日別集計。呼び出しスレッドで同期実行（IO逃しなし）。
     * これが「守ってくれない」側の実体。行数が多いほど全件走査・集計のコストが乗る。
     */
    fun getDailyStatsBlocking(): List<DailyStat> {
        val start = System.currentTimeMillis()
        val cursor = readableDatabase.rawQuery(
            "SELECT phase, durationSeconds, completedAtEpochMs FROM $TABLE_NAME", null
        )
        val rows = mutableListOf<Triple<String, Int, Long>>()
        cursor.use {
            while (it.moveToNext()) {
                val phase = it.getString(0)
                val duration = it.getInt(1)
                val completedAt = it.getLong(2)
                rows.add(Triple(phase, duration, completedAt))
            }
        }
        val result = rows
            .filter { (phase, _, _) -> phase == TimerPhase.FOCUS.name }
            .groupBy { (_, _, completedAt) -> completedAt / 86_400_000L }
            .map { (day, entries) ->
                DailyStat(
                    dateEpochDay = day,
                    focusCount = entries.size,
                    totalFocusSeconds = entries.sumOf { it.second },
                )
            }
            .sortedByDescending { it.dateEpochDay }
        val elapsed = System.currentTimeMillis() - start
        Log.d(
            TAG,
            "getDailyStatsBlocking: scanned ${rows.size} rows -> ${result.size} days in ${elapsed}ms"
        )
        return result
    }

    companion object {
        private const val TAG = "RawSqliteStatsHelper"
        private const val DB_NAME = "stats_raw.db"
        private const val DB_VERSION = 2
        private const val TABLE_NAME = "stats_raw_sessions"
        private const val LEGACY_TABLE_NAME = "focus_sessions"

        /**
         * デモ用シード行数の既定値。1件ごとに fsync が走るため、所要時間はほぼ行数に比例する。
         *
         * 実機校正の記録（エミュ API 37 / 2026-07-28）:
         *  - 3000行 → 約4.2秒。入力ディスパッチの5秒しきい値に届かず**ANRしなかった**
         *  - 5000行 → 約7秒。しきい値を安定して超える（この値を採用）
         * 端末が変われば再校正する（この定数だけ変えればよい）。TODO(登壇前): 本番端末で実測。
         */
        const val SEED_ROW_COUNT = 5000

        /** シードした completedAtEpochMs を分散させる日数幅。 */
        private const val SEED_SPAN_DAYS = 14
    }
}
