package com.uniccomj.jivana.data.repository

import androidx.room.withTransaction
import com.uniccomj.jivana.data.local.dao.HabitCycleDao
import com.uniccomj.jivana.data.local.database.JivanaDatabase
import com.uniccomj.jivana.data.local.entity.HabitCycleEntity
import com.uniccomj.jivana.data.local.entity.HabitCyclePauseEntity
import com.uniccomj.jivana.data.mapper.toDomain
import com.uniccomj.jivana.domain.model.HabitCycle
import com.uniccomj.jivana.domain.model.HabitCycleStatus
import com.uniccomj.jivana.domain.model.HabitPausePolicy
import com.uniccomj.jivana.domain.repository.HabitCycleRepository
import com.uniccomj.jivana.domain.repository.HabitOperationException.ActiveHabitsChanged
import com.uniccomj.jivana.domain.repository.HabitOperationException.PauseChangeRejected
import com.uniccomj.jivana.domain.repository.HabitPauseRejection
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomHabitCycleRepository @Inject constructor(
    private val database: JivanaDatabase,
    private val pausePolicy: HabitPausePolicy = HabitPausePolicy.Default
) : HabitCycleRepository {
    private val cycles get() = database.habitCycleDao()
    private val habits get() = database.habitDao()

    override fun observeCycle(habitId: String): Flow<HabitCycle?> =
        cycles.observeCycle(habitId).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun startFirstCycle(habitId: String, onDate: LocalDate): HabitCycle =
        database.withTransaction {
            cycles.getCycle(habitId)?.let { return@withTransaction it.toDomain() }
            val habit = habits.getById(habitId)?.takeIf { it.archivedAtEpochMillis == null }
                ?: throw ActiveHabitsChanged()
            cycles.insertCycle(habitId, maxOf(onDate, LocalDate.ofEpochDay(habit.startEpochDay)))
        }

    override suspend fun pause(habitId: String, onDate: LocalDate) = database.withTransaction {
        val habit = habits.getById(habitId)?.takeIf { it.archivedAtEpochMillis == null }
            ?: throw PauseChangeRejected(HabitPauseRejection.INACTIVE_CYCLE)
        val cycle = cycles.getCycle(habitId)?.toDomain()
            ?: throw PauseChangeRejected(HabitPauseRejection.INACTIVE_CYCLE)
        if (cycle.pauses.any { it.resumeDate == null || it.startDate == onDate }) {
            return@withTransaction
        }
        val epochDay = onDate.toEpochDay()
        val rejection = when {
            cycle.progressOn(onDate).status != HabitCycleStatus.ACTIVE ->
                HabitPauseRejection.INACTIVE_CYCLE

            epochDay < habit.scheduledThroughEpochDay ||
                cycle.pauses.any { it.resumeDate != null && it.resumeDate >= onDate } ->
                HabitPauseRejection.DATE_CONFLICT

            cycles.hasCompletionOnOrAfter(
                habitId,
                epochDay
            ) -> HabitPauseRejection.COMPLETION_EXISTS

            pausePolicy.remainingStarts(cycle) == 0 -> HabitPauseRejection.LIMIT_REACHED

            else -> null
        }
        if (rejection != null) throw PauseChangeRejected(rejection)
        cycles.insertPause(HabitCyclePauseEntity(cycle.id, epochDay, null))
    }

    override suspend fun resume(habitId: String, pauseStart: LocalDate, onDate: LocalDate) =
        database.withTransaction {
            val cycle = cycles.getCycle(habitId)?.toDomain() ?: return@withTransaction
            val open = cycle.pauses.any { it.startDate == pauseStart && it.resumeDate == null }
            if (!open) return@withTransaction
            if (cycle.archivedOn != null) {
                throw PauseChangeRejected(HabitPauseRejection.INACTIVE_CYCLE)
            }
            if (onDate < pauseStart) throw PauseChangeRejected(HabitPauseRejection.DATE_CONFLICT)
            cycles.closePause(cycle.id, pauseStart.toEpochDay(), onDate.toEpochDay())
        }
}

internal suspend fun HabitCycleDao.insertCycle(habitId: String, start: LocalDate): HabitCycle {
    val id = UUID.randomUUID().toString()
    insert(HabitCycleEntity(id, habitId, start.toEpochDay()))
    return HabitCycle(id, habitId, start)
}
