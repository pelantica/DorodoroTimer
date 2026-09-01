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

    // [ANR-01] デモ用シードの毎回リセット用。消すのはシード行だけで、実データ（isDemo=0）は残る。
    @Query("DELETE FROM focus_sessions WHERE isDemo = 1")
    suspend fun deleteDemo()
}
