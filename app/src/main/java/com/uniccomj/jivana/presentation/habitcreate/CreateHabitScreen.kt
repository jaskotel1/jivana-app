package com.uniccomj.jivana.presentation.habitcreate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uniccomj.jivana.R
import java.time.DayOfWeek

@Composable
fun CreateHabitRoute(viewModel: CreateHabitViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    BackHandler(enabled = state.saving) {
        /* Keep the transaction and its visible result together. */
    }
    CreateHabitScreen(
        state = state,
        onNameChange = viewModel::updateName,
        onDescriptionChange = viewModel::updateDescription,
        onFrequencyChange = viewModel::selectEveryDay,
        onDayClick = viewModel::toggleDay,
        onSave = viewModel::save,
        onBack = onBack
    )
}

@Composable
fun CreateHabitScreen(
    state: CreateHabitUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onFrequencyChange: (Boolean) -> Unit,
    onDayClick: (DayOfWeek) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(
            rememberScrollState()
        ).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.habit_create_title),
            style = MaterialTheme.typography.headlineSmall
        )
        HabitTextFields(state, onNameChange, onDescriptionChange)
        HabitFrequency(state, onFrequencyChange, onDayClick)
        Text(
            stringResource(R.string.habit_starts_today),
            style = MaterialTheme.typography.bodyMedium
        )
        state.saveFailure?.let { failure ->
            val message = when (failure) {
                HabitSaveFailure.ACTIVE_HABIT -> R.string.habit_active_limit
                HabitSaveFailure.REQUEST_TOKEN -> R.string.habit_token_conflict
                HabitSaveFailure.STORAGE -> R.string.habit_save_error
            }
            Text(
                stringResource(message),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("habit_save_error")
            )
        }
        Button(
            onClick = onSave,
            enabled = !state.saving && !state.saved,
            modifier = Modifier.fillMaxWidth().testTag("habit_save")
        ) {
            if (state.saving) {
                CircularProgressIndicator(
                    Modifier.padding(end = 8.dp).size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(stringResource(if (state.saving) R.string.habit_saving else R.string.habit_save))
        }
        TextButton(
            onClick = onBack,
            enabled = !state.saving,
            modifier = Modifier.testTag("habit_cancel")
        ) {
            Text(stringResource(R.string.habit_cancel))
        }
    }
}

@Composable
private fun HabitTextFields(
    state: CreateHabitUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.habit_name)) },
        placeholder = { Text(stringResource(R.string.habit_name_example)) },
        singleLine = true,
        enabled = !state.saving,
        isError = state.nameRequired,
        supportingText = {
            if (state.nameRequired) Text(stringResource(R.string.habit_name_required))
        },
        modifier = Modifier.fillMaxWidth().testTag("habit_name")
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = onDescriptionChange,
        label = { Text(stringResource(R.string.habit_description)) },
        minLines = 3,
        enabled = !state.saving,
        modifier = Modifier.fillMaxWidth().testTag("habit_description")
    )
}

@Composable
private fun HabitFrequency(
    state: CreateHabitUiState,
    onFrequencyChange: (Boolean) -> Unit,
    onDayClick: (DayOfWeek) -> Unit
) {
    Text(stringResource(R.string.habit_frequency), style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup()) {
        listOf(true, false).forEach { everyDay ->
            Row(
                Modifier.fillMaxWidth().testTag(if (everyDay) "habit_daily" else "habit_weekly")
                    .selectable(
                        selected = state.everyDay == everyDay,
                        enabled = !state.saving,
                        role = Role.RadioButton,
                        onClick = { onFrequencyChange(everyDay) }
                    ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.everyDay == everyDay,
                    onClick = null,
                    enabled = !state.saving
                )
                Text(
                    stringResource(
                        if (everyDay) R.string.habit_every_day else R.string.habit_selected_days
                    )
                )
            }
        }
    }
    if (!state.everyDay) {
        val shortNames = stringArrayResource(R.array.habit_weekdays_short)
        val fullNames = stringArrayResource(R.array.habit_weekdays_full)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in state.scheduledDays,
                    onClick = { onDayClick(day) },
                    enabled = !state.saving,
                    label = { Text(shortNames[day.ordinal]) },
                    modifier = Modifier.testTag("habit_day_${day.name}")
                        .semantics { contentDescription = fullNames[day.ordinal] }
                )
            }
        }
    }
    if (state.daysRequired) {
        Text(
            stringResource(R.string.habit_days_required),
            color = MaterialTheme.colorScheme.error
        )
    }
}
