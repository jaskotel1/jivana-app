package com.uniccomj.jivana.presentation.avatar

import com.uniccomj.jivana.domain.model.JiveCondition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JiveConditionController(initialCondition: JiveCondition = JiveCondition()) {
    private val mutableCondition = MutableStateFlow(initialCondition)

    val condition: StateFlow<JiveCondition> = mutableCondition.asStateFlow()

    fun updateCondition(condition: JiveCondition) {
        mutableCondition.value = condition
    }
}
