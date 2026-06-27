package com.tefumichangdev.dorodorotimer.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phase: String,
    val durationSeconds: Int,
    val completedAtEpochMs: Long,
)
