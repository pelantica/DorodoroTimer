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
     *
     * 投入するデータは [DemoStatsSeed]（両実装共通の生成仕様）から取る。ここの責務は
     * 「取得した行をどう書き込むか」だけ。わざと `beginTransaction()`/`endTransaction()` で
     * 囲まない。SQLite は明示トランザクション外の INSERT を「1文＝1トランザクション」として
     * 都度コミット（＝都度 fsync）するため、このループは `Thread.sleep` のような偽の重りではなく、
     * 「トランザクションで囲み忘れた INSERT ループ」という実務で実在する同期I/Oの重さそのものを再現する。
     *
     * [BlockingStatsRepository.dailyStats]（Main起点、withContext なし）から呼ばれるため、
     * この重い非トランザクションI/Oがそのままメインスレッドを専有し ANR を誘発する。
     * 対比: [OffloadedStatsRepository] は同じ [DemoStatsSeed] を、同じ非トランザクション・
     * 1件ずつの書き込みで投入するが、Room の suspend DAO 経由なので ANR しない。
     *
     * **毎回入れ直すのは再現性のため**。「空のときだけ」にすると初回しか ANR せず、
     * しかも ANR でプロセスが落ちると中途半端な行数が残って以後まったく再現しなくなる
     * （実機では 2166行が残り、集計のみ 8ms で完了して不発になった）。
     *
     * @param rowCount シード行数。実機ANR計測で校正するためデフォルト引数化してある
     *   （[DemoStatsSeed.SEED_ROW_COUNT] を変えるだけで負荷を調整できる）。テストは小さい値でロジックのみ検証する。
     * @param nowEpochMs シード生成の基準時刻。[DemoStatsSeed.generate] にそのまま渡す。
     */
    fun reseedForDemo(
        rowCount: Int = DemoStatsSeed.SEED_ROW_COUNT,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val db = writableDatabase
        // 前回のデモデータを消す（DELETE 自体は軽い。重いのは下の非トランザクションINSERTループ）。
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
    }
}
