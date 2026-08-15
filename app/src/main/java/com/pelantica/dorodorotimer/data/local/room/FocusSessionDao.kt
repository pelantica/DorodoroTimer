package com.pelantica.dorodorotimer.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FocusSessionDao {
    // suspend DAO は Room が IO スレッドへ逃してくれる（＝「守ってくれる」側）。
    @Insert
    suspend fun insert(entity: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions ORDER BY completedAtEpochMs DESC")
    suspend fun getAll(): List<FocusSessionEntity>

    // [ANR-01] デモ用シードの毎回リセット用。OffloadedStatsRepository が「1件ずつ非トランザクション
    // insert」の前段で呼ぶ。@Transaction は付けない（DELETE 自体は軽く、対比の対象は下のINSERTループ）。
    // 消すのはシード行だけ。実データ（isDemo=0）は demoMode を何度往復しても残る。
    @Query("DELETE FROM focus_sessions WHERE isDemo = 1")
    suspend fun deleteDemo()
}
