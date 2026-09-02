package com.pelantica.dorodorotimer.data.local.stats

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.pelantica.dorodorotimer.domain.model.DailyStat
import com.pelantica.dorodorotimer.domain.model.TimerPhase

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
        // デモ用DBなので破壊的でよい。旧テーブル名が残っていれば併せて掃除する。
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
     * わざとトランザクションで囲まない: SQLite は明示トランザクション外の INSERT を都度コミット
     * （＝都度 fsync）するため、「囲み忘れた INSERT ループ」という実在する同期I/Oの重さになる。
     * Main 起点の [BlockingStatsRepository.dailyStats] から呼ばれ、メインを専有して ANR を誘発する。
     * 毎回入れ直すのは再現性のため（中途半端な行数が残ると以後再現しなくなる）。
     */
    fun reseedForDemo(
        rowCount: Int = DemoStatsSeed.SEED_ROW_COUNT,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val db = writableDatabase
        // 前回のデモデータを消す（重いのは下の非トランザクションINSERTループ）。
        db.delete(TABLE_NAME, null, null)

        val start = System.currentTimeMillis()
        val rows = DemoStatsSeed.generate(rowCount, nowEpochMs)
        for (row in rows) {
            // [ANR-01] トランザクションで囲まない: 1件ごとにコミット＝1件ごとにfsync（本物の遅いI/O）
            insertBlocking(row.phase, row.durationSeconds, row.completedAtEpochMs)
        }
        val elapsed = System.currentTimeMillis() - start
        Log.d(TAG, "reseedForDemo: inserted $rowCount rows (non-transactional) in ${elapsed}ms")
    }

    /** 全件取得→Kotlin側でFOCUSのみ日別集計。呼び出しスレッドで同期実行（IO逃しなし）。 */
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
    }
}
