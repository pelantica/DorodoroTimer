package com.tefumichangdev.dorodorotimer.data.local.stats

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /** テスト用データ投入。本番では TimerViewModel がセッション完了時に Room 経由で書くため、
     *  この生SQLite DB はデモモード専用のスタンドアロン DB として機能する。 */
    fun insertBlocking(phase: String, durationSeconds: Int, completedAtEpochMs: Long) {
        writableDatabase.insert(TABLE_NAME, null, ContentValues().apply {
            put("phase", phase)
            put("durationSeconds", durationSeconds)
            put("completedAtEpochMs", completedAtEpochMs)
        })
    }

    /**
     * 全件取得→Kotlin側でFOCUSのみ日別集計。呼び出しスレッドで同期実行（IO逃しなし）。
     * これが「守ってくれない」側の実体。
     */
    fun getDailyStatsBlocking(): List<DailyStat> {
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
        return rows
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
    }

    companion object {
        private const val DB_NAME = "stats_raw.db"
        private const val DB_VERSION = 1
        private const val TABLE_NAME = "focus_sessions"
    }
}
