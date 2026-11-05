package com.uniccomj.jivana.presentation.avatar

import com.uniccomj.jivana.domain.model.HabitCompletionEvents
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveReaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JiveConditionControllerTest {
    @Test
    fun `updated condition is published by state flow`() {
        val controller = JiveConditionController(HabitCompletionEvents())
        val updated = JiveCondition(mood = JiveMood.HAPPY)

        controller.updateCondition(updated)

        assertEquals(updated, controller.condition.value)
    }

    @Test
    fun `completions celebrate repeatedly without mutating condition or replaying past events`() =
        runTest {
            val events = HabitCompletionEvents()
            val controller = JiveConditionController(events)
            val condition = JiveCondition(mood = JiveMood.SAD)
            controller.updateCondition(condition)
            events.notifyCompletion()
            val reactions = mutableListOf<JiveReaction>()
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                controller.reactions.collect { reactions += it }
            }
            assertEquals(emptyList<JiveReaction>(), reactions)

            repeat(2) { events.notifyCompletion() }
            controller.updateCondition(condition.copy(mood = JiveMood.HAPPY))
            assertEquals(List(2) { JiveReaction.CELEBRATING }, reactions)
            assertEquals(condition.copy(mood = JiveMood.HAPPY), controller.condition.value)

            collector.cancel()
            events.notifyCompletion()
            val recreated = JiveConditionController(events)
            val newReactions = mutableListOf<JiveReaction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                recreated.reactions.collect { newReactions += it }
            }
            assertEquals(emptyList<JiveReaction>(), newReactions)
        }
}
