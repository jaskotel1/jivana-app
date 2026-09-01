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
        WHERE schedule.dateEpochDay <= :throughEpochDay
        GROUP BY schedule.dateEpochDay
        ORDER BY schedule.dateEpochDay ASC
        """
    )
    abstract fun observeDailyPerformance(
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
