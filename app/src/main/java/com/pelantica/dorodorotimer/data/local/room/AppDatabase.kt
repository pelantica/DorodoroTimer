package com.pelantica.dorodorotimer.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * まだリリースしておらず、インストール実績が開発端末のみのため、スキーマ変更は
 * マイグレーションを書かずに**アプリを入れ直して**作り直す運用にしている（version は 1 のまま）。
 * ストアに出す時点で version を固定し、以降の変更は Migration を書くこと。
 */
@Database(entities = [FocusSessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusSessionDao(): FocusSessionDao
}
