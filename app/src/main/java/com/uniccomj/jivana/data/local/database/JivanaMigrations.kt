package com.uniccomj.jivana.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object JivanaMigrations {
    val Migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No foreign keys to legacy history and no fabricated definitions or weekly rules.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS habits (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    scheduledDaysMask INTEGER NOT NULL,
                    startEpochDay INTEGER NOT NULL,
                    archivedAtEpochMillis INTEGER,
                    scheduledThroughEpochDay INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
        }
    }

    val Migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE habits ADD COLUMN scheduleType TEXT NOT NULL DEFAULT 'LEGACY_WEEKDAYS'"
            )
            db.execSQL("ALTER TABLE habits ADD COLUMN weeklyTarget INTEGER")
            // Version 2 used the seven-bit mask 127 for a habit planned every day.
            db.execSQL("UPDATE habits SET scheduleType = 'DAILY' WHERE scheduledDaysMask = 127")
        }
    }

    val Migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Weekly completions do not have a planned day. Preserve all existing completion keys.
            db.execSQL(
                """
                CREATE TABLE habit_completions_new (
                    habitId TEXT NOT NULL,
                    dateEpochDay INTEGER NOT NULL,
                    PRIMARY KEY(habitId, dateEpochDay)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO habit_completions_new (habitId, dateEpochDay)
                SELECT habitId, dateEpochDay FROM habit_completions
                """.trimIndent()
            )
            db.execSQL("DROP TABLE habit_completions")
            db.execSQL("ALTER TABLE habit_completions_new RENAME TO habit_completions")
        }
    }
}
