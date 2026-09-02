package com.pelantica.dorodorotimer.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 未リリースのため、スキーマ変更はマイグレーションを書かずにアプリを入れ直す運用（version は 1 のまま）。
 * ストアに出す時点で version を固定し、以降の変更は Migration を書くこと。
 */
@Database(entities = [FocusSessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusSessionDao(): FocusSessionDao
}
