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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uniccomj.jivana.R
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.HabitValidationError

@Composable
fun CreateHabitRoute(viewModel: CreateHabitViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var expandedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    BackHandler(enabled = state.configuring) {
        viewModel.returnToCatalog()
    }
    if (state.configuring) {
        CreateHabitForm(state, viewModel)
    } else {
        HabitCatalogScreen(
            expandedCategory = expandedCategory,
            onCategoryClick = { expandedCategory = it.takeUnless { it == expandedCategory } },
            onTemplateClick = viewModel::openForm,
            onCustomClick = { viewModel.openForm() },
            onBack = onBack
        )
    }
}

@Composable
private fun CreateHabitForm(state: CreateHabitUiState, viewModel: CreateHabitViewModel) {
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(state.validationError) {
        if (state.validationError != null) scroll.scrollTo(0)
    }
    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(scroll).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.habit_create_title),
            style = MaterialTheme.typography.headlineSmall
        )
        HabitTextFields(state, viewModel::updateName, viewModel::updateDescription)
        HabitFrequency(state, viewModel::selectWeekly, viewModel::selectWeeklyTarget)
        HabitStartDate(
            selectedDate = state.startDate,
            enabled = state.canEdit,
            today = viewModel.today,
            onDateChange = viewModel::updateStartDate
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
            onClick = {
                focus.clearFocus()
                viewModel.save()
            },
            enabled = state.canEdit,
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
            onClick = viewModel::returnToCatalog,
            enabled = state.canEdit,
            modifier = Modifier.testTag("habit_cancel")
        ) {
            Text(stringResource(R.string.habit_catalog_back))
        }
    }
}

@Composable
private fun HabitTextFields(
    state: CreateHabitUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    val nameError = when (state.validationError) {
        HabitValidationError.NAME_REQUIRED -> stringResource(R.string.habit_name_required)

        HabitValidationError.NAME_TOO_LONG -> stringResource(
            R.string.habit_name_too_long,
            CreateHabitUseCase.MaxNameLength
        )

        else -> null
    }
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.habit_name)) },
        placeholder = { Text(stringResource(R.string.habit_name_example)) },
        singleLine = true,
        enabled = state.canEdit,
        isError = nameError != null,
        supportingText = { nameError?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth().testTag("habit_name")
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = onDescriptionChange,
        label = { Text(stringResource(R.string.habit_description)) },
        minLines = 3,
        enabled = state.canEdit,
        modifier = Modifier.fillMaxWidth().testTag("habit_description")
    )
}

@Composable
private fun HabitFrequency(
    state: CreateHabitUiState,
    onFrequencyChange: (Boolean) -> Unit,
    onTargetChange: (Int) -> Unit
) {
    Text(stringResource(R.string.habit_frequency), style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup()) {
        listOf(false, true).forEach { weekly ->
            Row(
                Modifier.fillMaxWidth().testTag(if (weekly) "habit_weekly" else "habit_daily")
                    .selectable(
                        selected = state.weekly == weekly,
                        enabled = state.canEdit,
                        role = Role.RadioButton,
                        onClick = { onFrequencyChange(weekly) }
                    ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.weekly == weekly,
                    onClick = null,
                    enabled = state.canEdit
                )
                Text(
                    stringResource(
                        if (weekly) R.string.habit_weekly_target else R.string.habit_every_day
                    )
                )
            }
        }
    }
    if (state.weekly) {
        WeeklyGoal(state.weeklyTarget, state.canEdit, onTargetChange)
    }
}

@Composable
private fun WeeklyGoal(target: Int, enabled: Boolean, onTargetChange: (Int) -> Unit) {
    Text(stringResource(R.string.habit_weekly_goal), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.habit_weekly_goal_hint))
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (goal in HabitSchedule.WeeklyTarget.AllowedTargets) {
            FilterChip(
                selected = target == goal,
                onClick = { onTargetChange(goal) },
                enabled = enabled,
                label = { Text(stringResource(R.string.habit_weekly_goal_number, goal)) },
                modifier = Modifier.testTag("habit_target_$goal")
            )
        }
    }
}
