package com.uniccomj.jivana.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.uniccomj.jivana.data.local.entity.HabitCycleEntity
import com.uniccomj.jivana.data.local.entity.HabitCyclePauseEntity
import kotlinx.coroutines.flow.Flow

data class HabitCycleWithPauses(
    @Embedded val cycle: HabitCycleEntity,
    @Relation(parentColumn = "id", entityColumn = "cycleId") val pauses: List<HabitCyclePauseEntity>
)

@Dao
interface HabitCycleDao {
    @Insert
    suspend fun insert(cycle: HabitCycleEntity)

    @Insert
    suspend fun insertPause(pause: HabitCyclePauseEntity)

    @Query(
        """
        UPDATE habit_cycle_pauses SET resumeEpochDay = :resumeEpochDay
        WHERE cycleId = :cycleId AND startEpochDay = :startEpochDay AND resumeEpochDay IS NULL
        """
    )
    suspend fun closePause(cycleId: String, startEpochDay: Long, resumeEpochDay: Long)

    @Query(
        """
        SELECT EXISTS (
            SELECT 1 FROM habit_completions WHERE habitId = :habitId AND dateEpochDay >= :epochDay
        )
        """
    )
    suspend fun hasCompletionOnOrAfter(habitId: String, epochDay: Long): Boolean

    @Transaction
    @Query("SELECT * FROM habit_cycles WHERE habitId = :habitId")
    suspend fun getCycle(habitId: String): HabitCycleWithPauses?

    @Transaction
    @Query("SELECT * FROM habit_cycles WHERE habitId = :habitId")
    fun observeCycle(habitId: String): Flow<HabitCycleWithPauses?>

    @Query("UPDATE habit_cycles SET archivedEpochDay = :epochDay WHERE habitId = :habitId")
    suspend fun archive(habitId: String, epochDay: Long)
}
