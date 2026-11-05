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
}
