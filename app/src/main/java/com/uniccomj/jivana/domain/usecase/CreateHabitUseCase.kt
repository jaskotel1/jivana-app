package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.repository.HabitRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

enum class HabitValidationError { NAME_REQUIRED, NAME_TOO_LONG, DAYS_REQUIRED }

class HabitValidationException(val error: HabitValidationError) :
    IllegalArgumentException(error.name)

class CreateHabitUseCase(private val repository: HabitRepository, private val clock: Clock) {
    fun today(): LocalDate = LocalDate.now(clock)

    suspend operator fun invoke(
        name: String,
        description: String,
        scheduledDays: Set<DayOfWeek>,
        requestToken: String = UUID.randomUUID().toString()
    ): Habit = create(name, description, requestToken) {
        if (scheduledDays.isEmpty()) {
            throw HabitValidationException(HabitValidationError.DAYS_REQUIRED)
        }
        if (scheduledDays.size == DayOfWeek.entries.size) {
            HabitSchedule.Daily
        } else {
            HabitSchedule.LegacyWeekdays(scheduledDays.toSet())
        }
    }

    suspend fun createWithSchedule(
        name: String,
        description: String,
        schedule: HabitSchedule,
        requestToken: String = UUID.randomUUID().toString(),
        startDate: LocalDate? = null
    ): Habit {
        val saved = create(name, description, requestToken, startDate) { schedule }
        // An implicit start can resolve to a different local date when retried after midnight.
        if (startDate != null && saved.startDate != startDate) {
            throw HabitOperationException.RequestTokenConflict()
        }
        return saved
    }

    private suspend fun create(
        name: String,
        description: String,
        requestToken: String,
        startDate: LocalDate? = null,
        resolveSchedule: () -> HabitSchedule
    ): Habit {
        val normalizedName = name.trim().replace(Whitespace, " ")
        if (normalizedName.isBlank()) {
            throw HabitValidationException(
                HabitValidationError.NAME_REQUIRED
            )
        }
        if (normalizedName.length > MaxNameLength) {
            throw HabitValidationException(HabitValidationError.NAME_TOO_LONG)
        }
        val schedule = resolveSchedule()
        require(requestToken.isNotBlank())
        val firstDay = startDate ?: today()
        val habit = Habit(
            id = UUID.nameUUIDFromBytes(requestToken.toByteArray(Charsets.UTF_8)).toString(),
            name = normalizedName,
            description = description.trim().takeIf(String::isNotEmpty),
            schedule = schedule,
            startDate = firstDay
        )
        val initialDates = if (schedule.isPlannedOn(firstDay.dayOfWeek)) {
            setOf(firstDay)
        } else {
            emptySet()
        }
        return repository.create(habit, initialDates)
    }

    companion object {
        const val MaxNameLength = 100
        private val Whitespace = Regex("\\s+")
    }
}
