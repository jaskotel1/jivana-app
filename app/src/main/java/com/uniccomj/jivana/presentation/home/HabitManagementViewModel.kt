package com.uniccomj.jivana.presentation.home

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.usecase.ManageActiveHabitsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitManagementState(
    val activeHabits: List<Habit>? = null,
    val working: Boolean = false,
    val failed: Boolean = false
)

@HiltViewModel
class HabitManagementViewModel @Inject constructor(private val manage: ManageActiveHabitsUseCase) :
    ViewModel() {
    private val mutableState = MutableStateFlow(HabitManagementState())
    val state = mutableState.asStateFlow()

    init {
        manage.observeActiveHabits()
            .onEach { habits -> mutableState.update { it.copy(activeHabits = habits) } }
            .catch { error ->
                if (error !is IOException && error !is SQLiteException) throw error
                mutableState.update { it.copy(failed = true) }
            }
            .launchIn(viewModelScope)
    }

    fun archive(id: String) = modify { manage.archive(id) }

    fun resolveConflict(keepId: String, expectedActiveIds: Set<String>) =
        modify { manage.resolveConflict(keepId, expectedActiveIds) }

    private fun modify(action: suspend () -> Unit) {
        if (state.value.working) return
        mutableState.update { it.copy(working = true, failed = false) }
        viewModelScope.launch {
            try {
                action()
            } catch (_: HabitOperationException.ActiveHabitsChanged) {
                mutableState.update { it.copy(failed = true) }
            } catch (_: IOException) {
                mutableState.update { it.copy(failed = true) }
            } catch (_: SQLiteException) {
                mutableState.update { it.copy(failed = true) }
            } finally {
                mutableState.update { it.copy(working = false) }
            }
        }
    }
}
