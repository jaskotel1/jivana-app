package com.uniccomj.jivana.presentation.habitcreate

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.HabitValidationError
import com.uniccomj.jivana.domain.usecase.HabitValidationException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CreateHabitViewModel @Inject constructor(
    private val createHabit: CreateHabitUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val requestToken =
        savedState.get<String>(RequestKey) ?: UUID.randomUUID().toString().also {
            savedState[RequestKey] = it
        }
    private val mutableState = MutableStateFlow(
        CreateHabitUiState(
            name = savedState[NameKey] ?: "",
            description = savedState[DescriptionKey] ?: "",
            scheduledDays = decodeDays(savedState[DaysKey] ?: AllDaysMask),
            everyDay = savedState[EveryDayKey] ?: true,
            saved = savedState[SavedKey] ?: false
        )
    )
    val state = mutableState.asStateFlow()

    fun updateName(value: String) {
        if (state.value.saving || state.value.saved) return
        savedState[NameKey] = value
        mutableState.value =
            state.value.copy(name = value, nameRequired = false, saveFailed = false)
    }

    fun updateDescription(value: String) {
        if (state.value.saving || state.value.saved) return
        savedState[DescriptionKey] = value
        mutableState.value = state.value.copy(description = value, saveFailed = false)
    }

    fun selectEveryDay(everyDay: Boolean) {
        if (state.value.saving || state.value.saved) return
        savedState[EveryDayKey] = everyDay
        if (everyDay) savedState[DaysKey] = AllDaysMask
        mutableState.value = state.value.copy(
            everyDay = everyDay,
            scheduledDays = if (everyDay) DayOfWeek.entries.toSet() else state.value.scheduledDays,
            daysRequired = false,
            saveFailed = false
        )
    }

    fun toggleDay(day: DayOfWeek) {
        if (state.value.saving || state.value.saved || state.value.everyDay) return
        val days = state.value.scheduledDays.let { if (day in it) it - day else it + day }
        savedState[DaysKey] = days.fold(0) { mask, selected -> mask or (1 shl selected.ordinal) }
        mutableState.value =
            state.value.copy(scheduledDays = days, daysRequired = false, saveFailed = false)
    }

    fun save() {
        val input = state.value
        if (input.saving || input.saved) return
        mutableState.value =
            input.copy(
                saving = true,
                nameRequired = false,
                daysRequired = false,
                saveFailed = false
            )
        viewModelScope.launch {
            try {
                createHabit(input.name, input.description, input.scheduledDays, requestToken)
                savedState[SavedKey] = true
                mutableState.value = state.value.copy(saving = false, saved = true)
            } catch (error: HabitValidationException) {
                mutableState.value = state.value.copy(
                    saving = false,
                    nameRequired = error.error == HabitValidationError.NAME_REQUIRED,
                    daysRequired = error.error == HabitValidationError.DAYS_REQUIRED
                )
            } catch (_: IOException) {
                showSaveFailure()
            } catch (_: SQLiteException) {
                showSaveFailure()
            }
        }
    }

    private fun showSaveFailure() {
        mutableState.value = state.value.copy(saving = false, saveFailed = true)
    }

    private fun decodeDays(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()

    private companion object {
        const val AllDaysMask = 127
        const val RequestKey = "habit.request"
        const val NameKey = "habit.name"
        const val DescriptionKey = "habit.description"
        const val DaysKey = "habit.days"
        const val EveryDayKey = "habit.everyDay"
        const val SavedKey = "habit.saved"
    }
}
