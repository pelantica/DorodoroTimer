package com.pelantica.dorodorotimer.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phase: String,
    val durationSeconds: Int,
    val completedAtEpochMs: Long,
    /**
     * [ANR-01] デモ用シードの行なら true。シードの入れ直しは isDemo=1 だけを消すので、
     * demoMode を行き来しても実データは消えない。
     */
    val isDemo: Boolean = false,
)
