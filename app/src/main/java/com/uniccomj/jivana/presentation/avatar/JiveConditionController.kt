package com.uniccomj.jivana.presentation.avatar

import com.uniccomj.jivana.domain.model.JiveCondition
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class JiveConditionController @Inject constructor() {
    private val mutableCondition = MutableStateFlow(JiveCondition())

    val condition: StateFlow<JiveCondition> = mutableCondition.asStateFlow()

    fun updateCondition(condition: JiveCondition) {
        mutableCondition.value = condition
    }
}
