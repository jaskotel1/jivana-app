package com.uniccomj.jivana.presentation.habitcreate

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.uniccomj.jivana.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HabitStartDate(
    selectedDate: LocalDate?,
    enabled: Boolean,
    today: () -> LocalDate,
    onDateChange: (LocalDate?) -> Unit
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val focus = LocalFocusManager.current
    Text(stringResource(R.string.habit_start_date), style = MaterialTheme.typography.titleMedium)
    Row {
        OutlinedButton(
            onClick = {
                focus.clearFocus()
                pickerOpen = true
            },
            enabled = enabled,
            modifier = Modifier.testTag("habit_start_date")
        ) {
            val label = selectedDate?.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            ) ?: stringResource(R.string.habit_start_date_today)
            Text(label)
        }
        if (selectedDate != null) {
            TextButton(onClick = { onDateChange(null) }, enabled = enabled) {
                Text(stringResource(R.string.habit_start_date_use_today))
            }
        }
    }
    if (pickerOpen) {
        HabitDatePicker(
            selectedDate = selectedDate,
            today = remember { today() },
            onDismiss = { pickerOpen = false },
            onConfirm = { date ->
                onDateChange(date)
                pickerOpen = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitDatePicker(
    selectedDate: LocalDate?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val initial = selectedDate ?: today
    val earliestMillis = today.toPickerMillis()
    val selectedMillis = selectedDate?.toPickerMillis()
    val firstYear = minOf(today.year, initial.year)
    val lastYear = maxOf(today.year, initial.year, DatePickerDefaults.YearRange.last)
    val selectableDates = remember(earliestMillis, selectedMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= earliestMillis || utcTimeMillis == selectedMillis
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toPickerMillis(),
        yearRange = firstYear..lastYear,
        selectableDates = selectableDates
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val instant = Instant.ofEpochMilli(millis)
                        onConfirm(instant.atOffset(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("habit_date_confirm")
            ) {
                Text(stringResource(R.string.habit_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.habit_cancel)) }
        }
    ) {
        DatePicker(state)
    }
}

private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
