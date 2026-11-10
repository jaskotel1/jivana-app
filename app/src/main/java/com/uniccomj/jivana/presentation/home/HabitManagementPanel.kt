package com.uniccomj.jivana.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uniccomj.jivana.R
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitCycleLengthDays
import com.uniccomj.jivana.domain.model.HabitCycleProgress
import com.uniccomj.jivana.domain.model.HabitCycleStatus
import com.uniccomj.jivana.domain.repository.HabitPauseRejection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HabitManagementPanel(
    state: HabitManagementState,
    onCreateHabit: () -> Unit,
    onArchive: (String) -> Unit,
    onResolveConflict: (String, Set<String>) -> Unit,
    onStartCycle: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String, LocalDate) -> Unit
) {
    var confirmation by remember { mutableStateOf<HabitConfirmation?>(null) }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val habits = state.activeHabits
        when {
            habits == null -> if (!state.failed) Text(stringResource(R.string.habit_loading))

            habits.isEmpty() -> AddHabitButton(onCreateHabit, enabled = !state.working)

            habits.size == 1 -> ActiveHabit(
                habit = habits.single(),
                state = state,
                onStartCycle = onStartCycle,
                onPause = onPause,
                onResume = onResume,
                onArchive = { confirmation = HabitConfirmation(it) }
            )

            else -> LegacyHabitChoices(habits, !state.working) { habit ->
                confirmation = HabitConfirmation(habit, habits.map { it.id }.toSet())
            }
        }
        if (state.failed) {
            Text(
                stringResource(R.string.habit_management_error),
                color = MaterialTheme.colorScheme.error
            )
        }
        state.pauseError?.let {
            val error = when (it) {
                HabitPauseRejection.INACTIVE_CYCLE -> R.string.habit_pause_inactive
                HabitPauseRejection.COMPLETION_EXISTS -> R.string.habit_pause_completion_exists
                HabitPauseRejection.DATE_CONFLICT -> R.string.habit_pause_date_conflict
                HabitPauseRejection.LIMIT_REACHED -> R.string.habit_pause_limit_reached
            }
            Text(stringResource(error), color = MaterialTheme.colorScheme.error)
        }
    }
    confirmation?.let { choice ->
        HabitConfirmationDialog(
            choice = choice,
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                val activeIds = choice.expectedActiveIds
                if (activeIds == null) {
                    onArchive(choice.habit.id)
                } else {
                    onResolveConflict(choice.habit.id, activeIds)
                }
            }
        )
    }
}

@Composable
private fun ActiveHabit(
    habit: Habit,
    state: HabitManagementState,
    onStartCycle: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String, LocalDate) -> Unit,
    onArchive: (Habit) -> Unit
) {
    val enabled = !state.working
    val progress = state.cycleProgress
    Text(habit.name, modifier = Modifier.testTag("habit_active_name"))
    Text(stringResource(R.string.habit_active_limit))
    if (progress == null) {
        TextButton(onClick = { onStartCycle(habit.id) }, enabled = enabled) {
            Text(stringResource(R.string.habit_cycle_start))
        }
    } else {
        CycleProgressText(progress)
        HabitPauseActions(
            progress = progress,
            enabled = enabled,
            habitId = habit.id,
            onPause = { onPause(habit.id) },
            onResume = { onResume(habit.id, it) }
        )
    }
    Button(
        onClick = { onArchive(habit) },
        enabled = enabled,
        modifier = Modifier.testTag("habit_archive_open")
    ) {
        Text(stringResource(R.string.habit_archive))
    }
}

@Composable
private fun HabitPauseActions(
    progress: HabitCycleProgress,
    enabled: Boolean,
    habitId: String,
    onPause: () -> Unit,
    onResume: (LocalDate) -> Unit
) {
    var confirming by rememberSaveable(habitId) { mutableStateOf(false) }
    when (progress.status) {
        HabitCycleStatus.ACTIVE -> {
            val pauseLabel = stringResource(R.string.habit_pause)
            Text(stringResource(R.string.habit_pause_remaining, progress.remainingPauses))
            Button(
                onClick = { confirming = true },
                enabled = enabled && progress.remainingPauses > 0,
                modifier = Modifier.testTag("habit_pause_open")
            ) {
                Text(pauseLabel)
            }
            if (confirming) {
                val explanation = stringResource(R.string.habit_pause_confirmation)
                val cancelLabel = stringResource(R.string.habit_cancel)
                AlertDialog(
                    onDismissRequest = { confirming = false },
                    title = { Text(pauseLabel) },
                    text = { Text(explanation) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirming = false
                                onPause()
                            },
                            enabled = enabled && progress.remainingPauses > 0,
                            modifier = Modifier.testTag("habit_pause_confirm")
                        ) {
                            Text(pauseLabel)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirming = false }) {
                            Text(cancelLabel)
                        }
                    }
                )
            }
        }

        HabitCycleStatus.PAUSED -> progress.pause?.let { pause ->
            Text(stringResource(R.string.habit_paused))
            Text(
                stringResource(
                    R.string.habit_pause_start_date,
                    pause.startDate.format(habitDateFormatter())
                )
            )
            Text(stringResource(R.string.habit_pause_days, pause.days))
            Button(
                onClick = { onResume(pause.startDate) },
                enabled = enabled && pause.canResume,
                modifier = Modifier.testTag("habit_resume")
            ) {
                Text(stringResource(R.string.habit_resume))
            }
        }

        else -> Unit
    }
}

@Composable
private fun CycleProgressText(progress: HabitCycleProgress) {
    val formatter = habitDateFormatter()
    val message = when (progress.status) {
        HabitCycleStatus.SCHEDULED ->
            stringResource(R.string.habit_cycle_scheduled, progress.startDate.format(formatter))

        HabitCycleStatus.ACTIVE -> stringResource(
            R.string.habit_cycle_day,
            progress.activeDays,
            HabitCycleLengthDays
        )

        HabitCycleStatus.PAUSED -> stringResource(
            R.string.habit_cycle_paused,
            progress.activeDays,
            HabitCycleLengthDays
        )

        HabitCycleStatus.COMPLETED -> stringResource(R.string.habit_cycle_completed)

        HabitCycleStatus.ARCHIVED -> return
    }
    Text(message, modifier = Modifier.testTag("habit_cycle_progress"))
    progress.endDate?.let {
        Text(
            stringResource(R.string.habit_cycle_end, it.format(formatter)),
            modifier = Modifier.testTag("habit_cycle_end")
        )
    }
}

@Composable
private fun habitDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalConfiguration.current.locales[0])

@Composable
private fun LegacyHabitChoices(habits: List<Habit>, enabled: Boolean, onKeep: (Habit) -> Unit) {
    Text(
        stringResource(R.string.habit_legacy_conflict),
        modifier = Modifier.testTag("habit_conflict")
    )
    for (habit in habits) {
        Button(
            onClick = { onKeep(habit) },
            enabled = enabled,
            modifier = Modifier.testTag("habit_keep_${habit.id}")
        ) {
            Column {
                Text(stringResource(R.string.habit_keep_choice, habit.name, habit.startDate))
                habit.description?.let { Text(it) }
            }
        }
    }
}

private data class HabitConfirmation(val habit: Habit, val expectedActiveIds: Set<String>? = null)

@Composable
private fun HabitConfirmationDialog(
    choice: HabitConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val resolvingConflict = choice.expectedActiveIds != null
    val message = if (resolvingConflict) {
        stringResource(R.string.habit_keep_confirmation, choice.habit.name)
    } else {
        stringResource(R.string.habit_archive_confirmation, choice.habit.name)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = if (resolvingConflict) R.string.habit_keep else R.string.habit_archive
            Text(stringResource(title))
        },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("habit_manage_confirm")) {
                val action = if (resolvingConflict) R.string.habit_keep else R.string.habit_archive
                Text(stringResource(action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.habit_cancel)) }
        }
    )
}
