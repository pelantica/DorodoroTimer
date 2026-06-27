package com.tefumichangdev.dorodorotimer.data.local.room

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
}
