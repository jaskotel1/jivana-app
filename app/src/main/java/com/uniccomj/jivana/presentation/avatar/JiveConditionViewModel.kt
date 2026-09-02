package com.uniccomj.jivana.presentation.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniccomj.jivana.domain.usecase.ObserveJiveConditionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JiveConditionViewModel @Inject constructor(
    private val observeJiveCondition: ObserveJiveConditionUseCase,
    private val controller: JiveConditionController,
    private val clock: Clock
) : ViewModel() {
    private val currentDate = MutableStateFlow(LocalDate.now(clock))

    val condition = controller.condition

    init {
        currentDate
            .flatMapLatest(observeJiveCondition::invoke)
            .onEach(controller::updateCondition)
            .launchIn(viewModelScope)
    }

    fun refreshForCurrentDay() {
        currentDate.value = LocalDate.now(clock)
    }
}
