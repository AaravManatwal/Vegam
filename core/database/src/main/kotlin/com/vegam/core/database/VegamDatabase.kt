package com.vegam.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DownloadEntity::class, SegmentEntity::class, DetectionEntity::class], version = 4, exportSchema = false)
abstract class VegamDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
    abstract fun segments(): SegmentDao
    abstract fun detections(): DetectionDao

    companion object {
        fun create(context: Context): VegamDatabase =
            Room.databaseBuilder(context.applicationContext, VegamDatabase::class.java, "vegam.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v1 (Phase 2) -> v2: SAF folder ke liye destUri column. Purani downloads safe rehti hain. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN destUri TEXT")
            }
        }

        /** v2 -> v3: Smart Detection history table. Purani downloads ko haath nahi lagta. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `detections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, `normalizedUrl` TEXT NOT NULL, `domain` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`fileType` TEXT NOT NULL, `mime` TEXT, `sizeBytes` INTEGER NOT NULL, `linkState` TEXT NOT NULL, " +
                        "`linkDetail` TEXT NOT NULL, `duplicate` TEXT NOT NULL, `securityLevel` TEXT NOT NULL, " +
                        "`warnings` TEXT NOT NULL, `source` TEXT NOT NULL, `decision` TEXT NOT NULL, " +
                        "`downloadId` INTEGER, `detectedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_detections_normalizedUrl` ON `detections` (`normalizedUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_detections_detectedAt` ON `detections` (`detectedAt`)")
            }
        }

        /** v3 -> v4: page se pakde downloads ke liye headers column (User-Agent/Referer/Cookie). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN headers TEXT")
            }
        }
    }
}
