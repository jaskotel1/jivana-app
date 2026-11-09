package com.uniccomj.jivana.domain.usecase

import com.uniccomj.jivana.domain.model.Habit
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
    ): Habit {
        val normalizedName = name.trim().replace(Whitespace, " ")
        if (normalizedName.isBlank()) {
            throw HabitValidationException(
                HabitValidationError.NAME_REQUIRED
            )
        }
        if (scheduledDays.isEmpty()) {
            throw HabitValidationException(
                HabitValidationError.DAYS_REQUIRED
            )
        }
        require(requestToken.isNotBlank())
        val today = LocalDate.now(clock)
        val habit = Habit(
            id = UUID.nameUUIDFromBytes(requestToken.toByteArray(Charsets.UTF_8)).toString(),
            name = normalizedName,
            description = description.trim().takeIf(String::isNotEmpty),
            scheduledDays = scheduledDays.toSet(),
            startDate = today
        )
        val initialDates = if (today.dayOfWeek in scheduledDays) setOf(today) else emptySet()
        return repository.create(habit, initialDates)
    }

    private companion object {
        val Whitespace = Regex("\\s+")
    }
}
