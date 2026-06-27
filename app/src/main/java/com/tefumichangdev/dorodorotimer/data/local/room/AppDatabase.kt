package com.tefumichangdev.dorodorotimer.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FocusSessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusSessionDao(): FocusSessionDao
}
