package com.uniccomj.jivana.presentation

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** Emits immediately when collected and again when the local date or time zone changes. */
internal fun localDateChanges(clock: Clock) = flow {
    var previousDate: LocalDate? = null
    var previousZone: ZoneId? = null
    while (currentCoroutineContext().isActive) {
        val date = LocalDate.now(clock)
        val zone = clock.zone
        if (date != previousDate || zone != previousZone) {
            emit(date)
            previousDate = date
            previousZone = zone
        }
        val midnight = date.plusDays(1).atStartOfDay(zone).toInstant()
        val remaining = Duration.between(clock.instant(), midnight).toMillis()
        delay(remaining.coerceIn(1L, MaximumDateCheckMillis))
    }
}

private const val MaximumDateCheckMillis = 60_000L
