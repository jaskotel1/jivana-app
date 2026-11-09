package com.uniccomj.jivana.presentation.habitcreate

import com.uniccomj.jivana.domain.model.HabitSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HabitCatalogTest {
    @Test
    fun `catalog groups all sixteen templates under stable unique identifiers`() {
        val expected = mapOf(
            "health" to listOf("drink_water", "eat_healthy", "limit_sweets", "eat_vegetables"),
            "activity" to listOf("walk", "workout", "run", "stretch"),
            "growth" to listOf("read", "learn_language", "meditate", "learn_skill"),
            "everyday" to listOf("tidy_up", "limit_phone", "plan_day", "journal")
        )
        val actual = HabitCatalog.associate { category ->
            category.id to category.templates.map { it.id }
        }
        assertEquals(expected, actual)
        val ids = HabitCatalog.flatMap { category -> category.templates.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `catalog suggests daily habits and supported weekly targets`() {
        val templates = HabitCatalog.flatMap { it.templates }
        assertTrue(templates.any { it.suggestedWeeklyTarget == null })
        val weekly = templates.mapNotNull { template ->
            template.suggestedWeeklyTarget?.let { template.id to HabitSchedule.WeeklyTarget(it) }
        }.toMap()
        assertEquals(
            mapOf("workout" to 3, "run" to 2, "learn_language" to 4, "learn_skill" to 3),
            weekly.mapValues { it.value.completionsPerWeek }
        )
    }
}
