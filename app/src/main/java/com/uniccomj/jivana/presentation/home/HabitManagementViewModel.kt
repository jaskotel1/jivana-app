package com.uniccomj.jivana.presentation.home

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniccomj.jivana.domain.model.Habit
import com.uniccomj.jivana.domain.model.HabitCycleProgress
import com.uniccomj.jivana.domain.repository.HabitOperationException
import com.uniccomj.jivana.domain.repository.HabitPauseRejection
import com.uniccomj.jivana.domain.usecase.ManageActiveHabitsUseCase
import com.uniccomj.jivana.domain.usecase.TrackHabitCycleUseCase
import com.uniccomj.jivana.presentation.localDateChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitManagementState(
    val activeHabits: List<Habit>? = null,
    val working: Boolean = false,
    val failed: Boolean = false,
    val cycleProgress: HabitCycleProgress? = null,
    val pauseError: HabitPauseRejection? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitManagementViewModel @Inject constructor(
    private val manage: ManageActiveHabitsUseCase,
    private val track: TrackHabitCycleUseCase,
    private val clock: Clock
) : ViewModel() {
    private val currentDate = MutableStateFlow(LocalDate.now(clock))
    private val mutableState = MutableStateFlow(HabitManagementState())
    val state = mutableState.asStateFlow()

    init {
        manage.observeActiveHabits()
            .flatMapLatest { habits ->
                val habit = habits.singleOrNull()
                val progress = if (habit == null) {
                    flowOf(null)
                } else {
                    track.observeProgress(habit.id, currentDate)
                }
                progress.map { habits to it }
            }
            .onEach { (habits, progress) ->
                mutableState.update { it.copy(activeHabits = habits, cycleProgress = progress) }
            }
            .catch { error ->
                if (error !is IOException && error !is SQLiteException) throw error
                mutableState.update { it.copy(failed = true) }
            }
            .launchIn(viewModelScope)
    }

    fun archive(id: String) = modify { manage.archive(id) }

    fun startCycle(id: String) = modify { track.start(id) }

    fun pause(id: String) = modify { track.pause(id) }

    fun resume(id: String, pauseStart: LocalDate) = modify { track.resume(id, pauseStart) }

    suspend fun refreshWhileResumed() {
        localDateChanges(clock).collect { date ->
            if (date != currentDate.value) mutableState.update { it.copy(pauseError = null) }
            currentDate.value = date
        }
    }

    fun resolveConflict(keepId: String, expectedActiveIds: Set<String>) =
        modify { manage.resolveConflict(keepId, expectedActiveIds) }

    private fun modify(action: suspend () -> Unit) {
        if (state.value.working) return
        mutableState.update { it.copy(working = true, failed = false, pauseError = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (_: HabitOperationException.ActiveHabitsChanged) {
                mutableState.update { it.copy(failed = true) }
            } catch (error: HabitOperationException.PauseChangeRejected) {
                mutableState.update { it.copy(pauseError = error.reason) }
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
