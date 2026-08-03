package com.pelantica.dorodorotimer.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FocusSessionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        /**
         * v1→v2: isDemo 列を追加（デモ用シードと実データの区別）。
         * 既存行はすべてタイマーで完了した実データなので DEFAULT 0（=実データ）でよい。
         * fallbackToDestructiveMigration は使わない: v1 の時点で本物のセッションが
         * 保存されており、破壊すると実データが消える。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
