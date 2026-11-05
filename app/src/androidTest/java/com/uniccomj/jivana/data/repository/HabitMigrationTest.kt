package com.uniccomj.jivana.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.local.database.JivanaMigrations
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HabitMigrationTest {
    @Test
    fun versionOneLegacyHistorySurvivesUpgradeAndDatabaseReopensForCreate() = runTest {
        val databaseName = "habit-migration-test"
        val date = LocalDate.of(2026, 11, 4)
        createVersionOneDatabase(databaseName).apply {
            execSQL(
                "INSERT INTO habit_schedules VALUES ('legacy-no-definition', ${date.toEpochDay()})"
            )
            execSQL(
                "INSERT INTO habit_completions VALUES ('legacy-no-definition', ${date.toEpochDay()})"
            )
            execSQL("INSERT INTO habit_day_interactions VALUES (${date.toEpochDay()})")
            close()
        }
        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JivanaDatabase::class.java,
            databaseName
        ).addMigrations(JivanaMigrations.Migration1To2).build()
        try {
            // Opening runs the production migration and Room's generated schema validation.
            assertEquals(2, database.openHelper.writableDatabase.version)
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM habits").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            val history =
                RoomHabitHistoryRepository(
                    database.habitHistoryDao(),
                    JiveScoringConfig(),
                    HabitCompletionEvents()
                )
            val repository = RoomHabitRepository(database, history)
            assertNull(repository.getById("legacy-no-definition"))
            val old = history.observeDailyPerformance(date).first().single()
            assertEquals(1, old.plannedHabitCount)
            assertEquals(1, old.completedHabitCount)
            assertEquals(true, old.activityRecorded)
            CreateHabitUseCase(
                repository,
                Clock.fixed(
                    date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                    ZoneOffset.UTC
                )
            )("New habit", "", DayOfWeek.entries.toSet())
            assertEquals(old, history.observeDailyPerformance(date).first().single())
        } finally {
            database.close()
        }
    }

    private fun createVersionOneDatabase(name: String): SQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name) // This name belongs exclusively to the migration fixture.
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open("${JivanaDatabase::class.java.canonicalName}/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        return SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                execSQL(
                    entity.getString(
                        "createSql"
                    ).replace("\u0024{TABLE_NAME}", entity.getString("tableName"))
                )
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) execSQL(setup.getString(index))
            version = 1
        }
    }
}
