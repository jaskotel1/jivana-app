package com.uniccomj.jivana.presentation.avatar

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniccomj.jivana.domain.usecase.ObserveJiveConditionUseCase
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JiveConditionViewModel @Inject constructor(
    private val observeJiveCondition: ObserveJiveConditionUseCase,
    private val controller: JiveConditionController,
    private val clock: Clock
) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0L)
    private val mutableRefreshFailed = MutableStateFlow(false)
    val refreshFailed = mutableRefreshFailed.asStateFlow()

    val condition = controller.condition
    val reactions = controller.reactions

    init {
        refreshRequests
            .flatMapLatest {
                val observedDate = LocalDate.now(clock)
                observeJiveCondition(observedDate)
                    .onStart { mutableRefreshFailed.value = false }
                    .onEach { condition ->
                        if (observedDate == LocalDate.now(clock)) {
                            controller.updateCondition(condition)
                        } else {
                            refreshForCurrentDay()
                        }
                    }
                    .catch { error ->
                        if (error !is IOException && error !is SQLiteException) throw error
                        mutableRefreshFailed.value = true
                    }
            }
            .launchIn(viewModelScope)
    }

    fun refreshForCurrentDay() {
        refreshRequests.value += 1
    }

    /** Lifecycle cancels this monitor in the background; resume always catches up first. */
    suspend fun refreshWhileResumed() {
        localDateChanges(clock).collect { refreshForCurrentDay() }
    }
}
