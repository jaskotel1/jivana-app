package com.uniccomj.jivana.presentation.avatar

import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveReaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

@Singleton
class JiveConditionController @Inject constructor(completionEvents: HabitCompletionEvents) {
    private val mutableCondition = MutableStateFlow(JiveCondition())

    val condition: StateFlow<JiveCondition> = mutableCondition.asStateFlow()

    // Collected by the visible character, independently from persistent condition updates.
    val reactions = completionEvents.completions.map { JiveReaction.CELEBRATING }

    fun updateCondition(condition: JiveCondition) {
        mutableCondition.value = condition
    }
}
