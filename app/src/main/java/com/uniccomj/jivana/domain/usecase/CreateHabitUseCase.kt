package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.repository.HabitRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

enum class HabitValidationError { NAME_REQUIRED, DAYS_REQUIRED }

class HabitValidationException(val error: HabitValidationError) :
    IllegalArgumentException(error.name)

class CreateHabitUseCase(private val repository: HabitRepository, private val clock: Clock) {
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
        requestToken: String = UUID.randomUUID().toString()
    ): Habit = create(name, description, requestToken) { schedule }

    private suspend fun create(
        name: String,
        description: String,
        requestToken: String,
        resolveSchedule: () -> HabitSchedule
    ): Habit {
        val normalizedName = name.trim().replace(Whitespace, " ")
        if (normalizedName.isBlank()) {
            throw HabitValidationException(
                HabitValidationError.NAME_REQUIRED
            )
        }
        val schedule = resolveSchedule()
        require(requestToken.isNotBlank())
        val today = LocalDate.now(clock)
        val habit = Habit(
            id = UUID.nameUUIDFromBytes(requestToken.toByteArray(Charsets.UTF_8)).toString(),
            name = normalizedName,
            description = description.trim().takeIf(String::isNotEmpty),
            schedule = schedule,
            startDate = today
        )
        val initialDates = if (schedule.isPlannedOn(today.dayOfWeek)) setOf(today) else emptySet()
        return repository.create(habit, initialDates)
    }

    private companion object {
        val Whitespace = Regex("\\s+")
    }
}
