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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HabitManagementPanel(
    state: HabitManagementState,
    onCreateHabit: () -> Unit,
    onArchive: (String) -> Unit,
    onResolveConflict: (String, Set<String>) -> Unit,
    onStartCycle: (String) -> Unit
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
                enabled = !state.working,
                progress = state.cycleProgress,
                onStartCycle = onStartCycle,
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
    enabled: Boolean,
    progress: HabitCycleProgress?,
    onStartCycle: (String) -> Unit,
    onArchive: (Habit) -> Unit
) {
    Text(habit.name, modifier = Modifier.testTag("habit_active_name"))
    Text(stringResource(R.string.habit_active_limit))
    if (progress == null) {
        TextButton(onClick = { onStartCycle(habit.id) }, enabled = enabled) {
            Text(stringResource(R.string.habit_cycle_start))
        }
    } else {
        CycleProgressText(progress)
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
private fun CycleProgressText(progress: HabitCycleProgress) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalConfiguration.current.locales[0])
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
