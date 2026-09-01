package com.uniccomj.jivana.presentation.avatar

import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveMood
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiveConditionControllerTest {
    @Test
    fun `updated condition is published by state flow`() {
        val controller = JiveConditionController()
        val updated = JiveCondition(mood = JiveMood.HAPPY)

        controller.updateCondition(updated)

        assertEquals(updated, controller.condition.value)
    }
}
