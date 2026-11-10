package com.uniccomj.jivana.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.local.database.JivanaMigrations
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.EnsureHabitSchedulesUseCase
import com.uniccomj.jivana.domain.usecase.JiveScoringConfig
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        createLegacyDatabase(databaseName, sourceVersion = 1).apply {
            execSQL(
                "INSERT INTO habit_schedules VALUES ('legacy-no-definition', ${date.toEpochDay()})"
            )
            execSQL(
                "INSERT INTO habit_completions VALUES ('legacy-no-definition', ${date.toEpochDay()})"
            )
            execSQL("INSERT INTO habit_day_interactions VALUES (${date.toEpochDay()})")
            close()
        }
        val database = openMigratedDatabase(databaseName)
        try {
            // Opening runs the production migration and Room's generated schema validation.
            assertEquals(5, database.openHelper.writableDatabase.version)
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM habits").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            val history =
                RoomHabitHistoryRepository(
                    database,
                    JiveScoringConfig(),
                    HabitCompletionEvents(),
                    clockOn(date)
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

    @Test
    fun versionTwoKeepsDailyAndLegacyHabitsAndHistoryAfterReopen() = runTest {
        val name = "habit-migration-v2-test"
        val start = LocalDate.of(2026, 11, 2)
        val archivedAt = Instant.parse("2026-11-03T09:00:00Z")
        val cursor = start.plusDays(1)
        createLegacyDatabase(name, sourceVersion = 2).use { fixture ->
            fixture.execSQL(
                "INSERT INTO habits VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "daily",
                    "Water",
                    "At lunch",
                    127,
                    start.toEpochDay(),
                    archivedAt.toEpochMilli(),
                    cursor.toEpochDay()
                )
            )
            fixture.execSQL(
                "INSERT INTO habits VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "weekdays",
                    "Run",
                    null,
                    17,
                    start.toEpochDay(),
                    null,
                    cursor.toEpochDay()
                )
            )
            for (id in listOf("daily", "weekdays", "orphan")) {
                fixture.execSQL(
                    "INSERT INTO habit_schedules VALUES (?, ?)",
                    arrayOf<Any>(id, start.toEpochDay())
                )
            }
            for (id in listOf("weekdays", "orphan")) {
                fixture.execSQL(
                    "INSERT INTO habit_completions VALUES (?, ?)",
                    arrayOf<Any>(id, start.toEpochDay())
                )
            }
            fixture.execSQL(
                "INSERT INTO habit_day_interactions VALUES (?)",
                arrayOf(start.toEpochDay())
            )
        }
        val migrated = openMigratedDatabase(name)
        try {
            assertEquals(5, migrated.openHelper.writableDatabase.version)
        } finally {
            migrated.close()
        }

        val reopened = openMigratedDatabase(name)
        try {
            val history = RoomHabitHistoryRepository(
                reopened,
                JiveScoringConfig(),
                HabitCompletionEvents(),
                clockOn(start)
            )
            val repository = RoomHabitRepository(reopened, history)
            assertEquals(
                Habit("daily", "Water", "At lunch", HabitSchedule.Daily, start, archivedAt),
                repository.getById("daily")
            )
            val weekdays = HabitSchedule.LegacyWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            assertEquals(
                Habit("weekdays", "Run", null, weekdays, start),
                repository.getById("weekdays")
            )
            assertEquals(
                cursor.toEpochDay(),
                reopened.habitDao().getById("weekdays")?.scheduledThroughEpochDay
            )
            assertNull(repository.getById("orphan"))
            assertNull(RoomHabitCycleRepository(reopened).observeCycle("weekdays").first())
            assertLegacyHistory(reopened, start)

            val end = start.plusWeeks(1)
            val clock = Clock.fixed(end.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
            val ensure = EnsureHabitSchedulesUseCase(repository, clock)
            ensure()
            ensure()
            val performance = history.observeDailyPerformance(end).first()
            assertEquals(listOf(start, start.plusDays(4), end), performance.map { it.date })
            assertEquals(listOf(3, 1, 1), performance.map { it.plannedHabitCount })
            assertLegacyHistory(reopened, start)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun versionThreePreservesCompletionsAndAllowsWeeklyDatesWithoutPlans() = runTest {
        val name = "habit-migration-v3-test"
        val date = LocalDate.of(2026, 11, 2)
        val epochDay = date.toEpochDay()
        createLegacyDatabase(name, sourceVersion = 3).use { fixture ->
            fixture.execSQL(
                """
                INSERT INTO habits VALUES
                ('daily', 'Read', NULL, 127, ?, NULL, ?, 'DAILY', NULL),
                ('weekly', 'Swim', NULL, 0, ?, NULL, ?, 'WEEKLY_TARGET', 3)
                """.trimIndent(),
                arrayOf(epochDay, epochDay, epochDay, epochDay)
            )
            for (id in listOf("daily", "orphan")) {
                val args = arrayOf<Any>(id, epochDay)
                fixture.execSQL("INSERT INTO habit_schedules VALUES (?, ?)", args)
                fixture.execSQL(
                    "INSERT INTO habit_completions VALUES (?, ?)",
                    args
                )
            }
            fixture.execSQL("INSERT INTO habit_day_interactions VALUES (?)", arrayOf(epochDay))
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = openMigratedDatabase(name)
        try {
            assertEquals(5, database.openHelper.writableDatabase.version)
            val history = RoomHabitHistoryRepository(
                database,
                JiveScoringConfig(),
                HabitCompletionEvents(),
                clockOn(date)
            )
            for (id in listOf("daily", "orphan")) {
                val dates = history.observeCompletedDates(id, date, date).first()
                assertEquals(listOf(date), dates)
            }
            val before = history.observeDailyPerformance(date).first()
            assertEquals(2, before.single().plannedHabitCount)
            assertEquals(2, before.single().completedHabitCount)
            assertEquals(true, before.single().activityRecorded)

            history.setHabitCompleted("weekly", date, true)
            val weeklyDates = history.observeCompletedDates("weekly", date, date).first()
            assertEquals(listOf(date), weeklyDates)
            assertEquals(before, history.observeDailyPerformance(date).first())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun versionFourPreservesWeeklyCompletionsAndStartsAnExistingCycleOnlyOnRequest() = runTest {
        val name = "habit-migration-v4-test"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val start = LocalDate.of(2026, 11, 2)
        val optInDate = start.plusWeeks(2)
        createLegacyDatabase(name, sourceVersion = 4).use { fixture ->
            fixture.execSQL(
                "INSERT INTO habits VALUES ('weekly', 'Swim', NULL, 0, ?, NULL, ?, 'WEEKLY_TARGET', 3)",
                arrayOf(start.toEpochDay(), start.toEpochDay())
            )
            fixture.execSQL(
                "INSERT INTO habit_completions VALUES ('weekly', ?)",
                arrayOf(start.toEpochDay())
            )
        }
        try {
            val saved = withMigratedDatabase(name) { db ->
                val history =
                    RoomHabitHistoryRepository(
                        db,
                        JiveScoringConfig(),
                        HabitCompletionEvents(),
                        clockOn(optInDate)
                    )
                val repository = RoomHabitCycleRepository(db)
                assertEquals(5, db.openHelper.writableDatabase.version)
                assertNull(repository.observeCycle("weekly").first())
                val starts = List(3) {
                    async { repository.startFirstCycle("weekly", optInDate) }
                }.awaitAll()
                assertEquals(1, starts.toSet().size)
                assertEquals(optInDate, starts.first().startDate)
                assertEquals(
                    listOf(start),
                    history.observeCompletedDates("weekly", start, optInDate).first()
                )
                starts.first()
            }
            withMigratedDatabase(name) { db ->
                assertEquals(saved.id, db.habitCycleDao().getCycle("weekly")?.cycle?.id)
                assertEquals(start.toEpochDay(), db.habitDao().getById("weekly")?.startEpochDay)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private inline fun <T> withMigratedDatabase(name: String, block: (JivanaDatabase) -> T): T {
        val database = openMigratedDatabase(name)
        try {
            return block(database)
        } finally {
            database.close()
        }
    }

    private fun assertLegacyHistory(database: JivanaDatabase, date: LocalDate) {
        val sqlite = database.openHelper.readableDatabase
        sqlite.query("SELECT habitId, dateEpochDay FROM habit_schedules ORDER BY habitId").use {
            val ids = buildList {
                while (it.moveToNext()) {
                    if (it.getLong(1) == date.toEpochDay()) add(it.getString(0))
                }
            }
            assertEquals(listOf("daily", "orphan", "weekdays"), ids)
        }
        sqlite.query("SELECT habitId, dateEpochDay FROM habit_completions ORDER BY habitId").use {
            val ids = buildList {
                while (it.moveToNext()) {
                    assertEquals(date.toEpochDay(), it.getLong(1))
                    add(it.getString(0))
                }
            }
            assertEquals(listOf("orphan", "weekdays"), ids)
        }
        sqlite.query("SELECT dateEpochDay FROM habit_day_interactions").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(date.toEpochDay(), it.getLong(0))
        }
    }

    private fun openMigratedDatabase(name: String): JivanaDatabase = Room.databaseBuilder(
        ApplicationProvider.getApplicationContext(),
        JivanaDatabase::class.java,
        name
    ).addMigrations(
        JivanaMigrations.Migration1To2,
        JivanaMigrations.Migration2To3,
        JivanaMigrations.Migration3To4,
        JivanaMigrations.Migration4To5
    ).build()

    private fun clockOn(date: LocalDate): Clock =
        Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private fun createLegacyDatabase(name: String, sourceVersion: Int): SQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name) // This name belongs exclusively to the migration fixture.
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open("${JivanaDatabase::class.java.canonicalName}/$sourceVersion.json")
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
            version = sourceVersion
        }
    }
}
