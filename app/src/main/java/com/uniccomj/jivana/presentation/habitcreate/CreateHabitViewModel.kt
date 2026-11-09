package com.uniccomj.jivana.presentation.habitcreate

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniccomj.jivana.domain.model.HabitSchedule
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.usecase.CreateHabitUseCase
import com.uniccomj.jivana.domain.usecase.HabitValidationException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDate
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
            configuring = savedState[ConfiguringKey] ?: false,
            name = savedState[NameKey] ?: "",
            description = savedState[DescriptionKey] ?: "",
            weekly = savedState[WeeklyKey] ?: false,
            weeklyTarget = savedState.get<Int>(WeeklyTargetKey)
                ?.takeIf { it in HabitSchedule.WeeklyTarget.AllowedTargets } ?: DefaultWeeklyTarget,
            startDate = savedState.get<Long>(StartDateKey)?.let(LocalDate::ofEpochDay),
            saved = savedState[SavedKey] ?: false
        )
    )
    val state = mutableState.asStateFlow()
    internal val today = createHabit::today

    internal fun openForm(
        template: HabitTemplate? = null,
        name: String = "",
        description: String = ""
    ) {
        val current = state.value
        if (current.configuring || current.saving || current.saved) return
        val id = template?.id ?: CustomChoice
        val weeklyTarget = template?.suggestedWeeklyTarget
        val next = if (savedState.get<String>(ChoiceKey) == id) {
            current.copy(configuring = true)
        } else {
            savedState[ChoiceKey] = id
            CreateHabitUiState(
                configuring = true,
                name = name,
                description = description,
                weekly = weeklyTarget != null,
                weeklyTarget = weeklyTarget ?: DefaultWeeklyTarget
            )
        }
        persistDraft(next)
    }

    fun returnToCatalog() {
        if (!state.value.canEdit) return
        persistDraft(state.value.copy(configuring = false))
    }

    fun updateName(value: String) = edit {
        copy(name = value, validationError = null)
    }

    fun updateDescription(value: String) = edit { copy(description = value) }

    fun selectWeekly(weekly: Boolean) = edit { copy(weekly = weekly) }

    fun selectWeeklyTarget(target: Int) {
        if (target in HabitSchedule.WeeklyTarget.AllowedTargets) {
            edit { copy(weeklyTarget = target) }
        }
    }

    fun updateStartDate(date: LocalDate?) = edit { copy(startDate = date) }

    private inline fun edit(update: CreateHabitUiState.() -> CreateHabitUiState) {
        val current = state.value
        if (current.canEdit) persistDraft(current.update().copy(saveFailure = null))
    }

    private fun persistDraft(draft: CreateHabitUiState) {
        savedState[ConfiguringKey] = draft.configuring
        savedState[NameKey] = draft.name
        savedState[DescriptionKey] = draft.description
        savedState[WeeklyKey] = draft.weekly
        savedState[WeeklyTargetKey] = draft.weeklyTarget
        savedState[StartDateKey] = draft.startDate?.toEpochDay()
        mutableState.value = draft
    }

    fun save() {
        val input = state.value
        if (!input.canEdit) return
        val draft = input.copy(validationError = null, saveFailure = null)
        mutableState.value = draft.copy(saving = true)
        viewModelScope.launch {
            mutableState.value = try {
                val schedule = if (input.weekly) {
                    HabitSchedule.WeeklyTarget(input.weeklyTarget)
                } else {
                    HabitSchedule.Daily
                }
                createHabit.createWithSchedule(
                    name = input.name,
                    description = input.description,
                    schedule = schedule,
                    requestToken = requestToken,
                    startDate = input.startDate
                )
                savedState[SavedKey] = true
                draft.copy(saved = true)
            } catch (error: HabitValidationException) {
                draft.copy(validationError = error.error)
            } catch (_: HabitOperationException.ActiveHabitLimitReached) {
                draft.copy(saveFailure = HabitSaveFailure.ACTIVE_HABIT)
            } catch (_: HabitOperationException.RequestTokenConflict) {
                draft.copy(saveFailure = HabitSaveFailure.REQUEST_TOKEN)
            } catch (_: IOException) {
                draft.copy(saveFailure = HabitSaveFailure.STORAGE)
            } catch (_: SQLiteException) {
                draft.copy(saveFailure = HabitSaveFailure.STORAGE)
            }
        }
    }

    private companion object {
        const val RequestKey = "habit.request"
        const val ChoiceKey = "habit.choice"
        const val ConfiguringKey = "habit.configuring"
        const val NameKey = "habit.name"
        const val DescriptionKey = "habit.description"
        const val WeeklyKey = "habit.weekly"
        const val WeeklyTargetKey = "habit.weeklyTarget"
        const val StartDateKey = "habit.startDate"
        const val SavedKey = "habit.saved"
        const val CustomChoice = "custom"
    }
}
