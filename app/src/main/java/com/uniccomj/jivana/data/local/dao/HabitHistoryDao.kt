package com.uniccomj.jivana.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.uniccomj.jivana.data.local.entity.HabitCompletionEntity
import com.uniccomj.jivana.data.local.entity.HabitDayInteractionEntity
import com.uniccomj.jivana.data.local.entity.HabitScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HabitHistoryDao {
    @Query(
        """
        WITH daily AS (
            SELECT
                schedule.dateEpochDay AS dateEpochDay,
                COUNT(*) AS plannedHabitCount,
                SUM(CASE WHEN completion.habitId IS NULL THEN 0 ELSE 1 END)
                    AS completedHabitCount,
                MAX(CASE WHEN interaction.dateEpochDay IS NULL THEN 0 ELSE 1 END)
                    AS activityRecorded
            FROM habit_schedules AS schedule
            LEFT JOIN habit_completions AS completion
                ON completion.habitId = schedule.habitId
                AND completion.dateEpochDay = schedule.dateEpochDay
            LEFT JOIN habit_day_interactions AS interaction
                ON interaction.dateEpochDay = schedule.dateEpochDay
            WHERE schedule.dateEpochDay <= :throughEpochDay
            GROUP BY schedule.dateEpochDay
        ),
        energy_boundary AS (
            SELECT MAX(candidate.dateEpochDay) AS startEpochDay
            FROM daily AS candidate
            WHERE (
                SELECT COUNT(*)
                FROM (
                    SELECT
                        subsequent.completedHabitCount,
                        subsequent.plannedHabitCount
                    FROM daily AS subsequent
                    WHERE subsequent.dateEpochDay >= candidate.dateEpochDay
                    ORDER BY subsequent.dateEpochDay ASC
                    LIMIT :energyFullRecoveryDays
                )
                WHERE completedHabitCount * 1.0 / plannedHabitCount >=
                    :energyRecoveryThreshold
            ) = :energyFullRecoveryDays
            OR (
                SELECT COUNT(*)
                FROM (
                    SELECT
                        subsequent.completedHabitCount,
                        subsequent.plannedHabitCount
                    FROM daily AS subsequent
                    WHERE subsequent.dateEpochDay >= candidate.dateEpochDay
                    ORDER BY subsequent.dateEpochDay ASC
                    LIMIT :exhaustedAfterWeakDays
                )
                WHERE completedHabitCount * 1.0 / plannedHabitCount <=
                    :weakEnergyThreshold
            ) = :exhaustedAfterWeakDays
        ),
        sleepiness_boundary AS (
            SELECT MAX(candidate.dateEpochDay) AS startEpochDay
            FROM daily AS candidate
            WHERE (
                SELECT COUNT(*)
                FROM (
                    SELECT
                        subsequent.completedHabitCount,
                        subsequent.plannedHabitCount
                    FROM daily AS subsequent
                    WHERE subsequent.dateEpochDay >= candidate.dateEpochDay
                    ORDER BY subsequent.dateEpochDay ASC
                    LIMIT :sleepinessFullRecoveryDays
                )
                WHERE completedHabitCount * 1.0 / plannedHabitCount >=
                    :sleepinessRecoveryThreshold
            ) = :sleepinessFullRecoveryDays
            OR (
                SELECT COUNT(*)
                FROM (
                    SELECT subsequent.activityRecorded
                    FROM daily AS subsequent
                    WHERE subsequent.dateEpochDay >= candidate.dateEpochDay
                    ORDER BY subsequent.dateEpochDay ASC
                    LIMIT :sleepingAfterInactiveDays
                )
                WHERE activityRecorded = 0
            ) = :sleepingAfterInactiveDays
        ),
        mood_boundary AS (
            SELECT MIN(dateEpochDay) AS startEpochDay
            FROM (
                SELECT dateEpochDay
                FROM daily
                ORDER BY dateEpochDay DESC
                LIMIT :moodRollingWindowDays
            )
        )
        SELECT CASE
            WHEN energy_boundary.startEpochDay IS NULL
                OR sleepiness_boundary.startEpochDay IS NULL
                OR mood_boundary.startEpochDay IS NULL
            THEN NULL
            ELSE MIN(
                energy_boundary.startEpochDay,
                sleepiness_boundary.startEpochDay,
                mood_boundary.startEpochDay
            )
        END
        FROM energy_boundary, sleepiness_boundary, mood_boundary
        """
    )
    abstract fun observeJiveHistoryStartEpochDay(
        throughEpochDay: Long,
        moodRollingWindowDays: Int,
        weakEnergyThreshold: Double,
        energyRecoveryThreshold: Double,
        exhaustedAfterWeakDays: Int,
        energyFullRecoveryDays: Int,
        sleepinessRecoveryThreshold: Double,
        sleepingAfterInactiveDays: Int,
        sleepinessFullRecoveryDays: Int
    ): Flow<Long?>

    @Query(
        """
        SELECT
            schedule.dateEpochDay AS dateEpochDay,
            COUNT(*) AS plannedHabitCount,
            SUM(CASE WHEN completion.habitId IS NULL THEN 0 ELSE 1 END) AS completedHabitCount,
            MAX(CASE WHEN interaction.dateEpochDay IS NULL THEN 0 ELSE 1 END) AS activityRecorded
        FROM habit_schedules AS schedule
        LEFT JOIN habit_completions AS completion
            ON completion.habitId = schedule.habitId
            AND completion.dateEpochDay = schedule.dateEpochDay
        LEFT JOIN habit_day_interactions AS interaction
            ON interaction.dateEpochDay = schedule.dateEpochDay
        WHERE schedule.dateEpochDay BETWEEN :fromEpochDay AND :throughEpochDay
        GROUP BY schedule.dateEpochDay
        ORDER BY schedule.dateEpochDay ASC
        """
    )
    abstract fun observeDailyPerformance(
        fromEpochDay: Long,
        throughEpochDay: Long
    ): Flow<List<DailyHabitPerformanceRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSchedules(schedules: List<HabitScheduleEntity>)

    @Transaction
    open suspend fun setHabitCompleted(habitId: String, dateEpochDay: Long, completed: Boolean) {
        if (completed) {
            insertCompletion(HabitCompletionEntity(habitId, dateEpochDay))
        } else {
            deleteCompletion(HabitCompletionEntity(habitId, dateEpochDay))
        }
        insertInteraction(HabitDayInteractionEntity(dateEpochDay))
    }

    @Transaction
    open suspend fun recordDailyCheckIn(dateEpochDay: Long) {
        insertInteraction(HabitDayInteractionEntity(dateEpochDay))
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCompletion(completion: HabitCompletionEntity)

    @Delete
    protected abstract suspend fun deleteCompletion(completion: HabitCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertInteraction(interaction: HabitDayInteractionEntity)
}
