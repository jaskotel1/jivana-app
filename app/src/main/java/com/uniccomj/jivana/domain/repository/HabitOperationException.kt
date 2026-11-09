package com.uniccomj.jivana.domain.repository

sealed class HabitOperationException(message: String) : IllegalArgumentException(message) {
    class ActiveHabitLimitReached : HabitOperationException("An active habit already exists")

    class RequestTokenConflict :
        HabitOperationException("Request token already used for another habit")

    class ActiveHabitsChanged : HabitOperationException("The active habits have changed")

    class CompletionRejected(val reason: HabitCompletionRejection) :
        HabitOperationException(reason.name)
}

enum class HabitCompletionRejection { FUTURE_DATE, BEFORE_START, ARCHIVED, NOT_FOUND, UNSCHEDULED }
