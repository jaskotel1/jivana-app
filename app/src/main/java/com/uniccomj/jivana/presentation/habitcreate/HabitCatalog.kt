package com.uniccomj.jivana.presentation.habitcreate

import androidx.annotation.StringRes
import com.uniccomj.jivana.R

internal data class HabitCategory(
    val id: String,
    @param:StringRes val title: Int,
    val templates: List<HabitTemplate>
)

internal data class HabitTemplate(
    val id: String,
    @param:StringRes val name: Int,
    @param:StringRes val description: Int? = null,
    val suggestedWeeklyTarget: Int? = null
)

internal val HabitCatalog = listOf(
    HabitCategory(
        "health",
        R.string.habit_category_health,
        listOf(
            HabitTemplate(
                "drink_water",
                R.string.habit_template_drink_water,
                R.string.habit_template_drink_water_description
            ),
            HabitTemplate(
                "eat_healthy",
                R.string.habit_template_eat_healthy,
                R.string.habit_template_eat_healthy_description
            ),
            HabitTemplate(
                "limit_sweets",
                R.string.habit_template_limit_sweets,
                R.string.habit_template_limit_sweets_description
            ),
            HabitTemplate(
                "eat_vegetables",
                R.string.habit_template_eat_vegetables,
                R.string.habit_template_eat_vegetables_description
            )
        )
    ),
    HabitCategory(
        "activity",
        R.string.habit_category_activity,
        listOf(
            HabitTemplate(
                "walk",
                R.string.habit_template_walk,
                R.string.habit_template_walk_description
            ),
            HabitTemplate(
                "workout",
                R.string.habit_template_workout,
                R.string.habit_template_workout_description,
                suggestedWeeklyTarget = 3
            ),
            HabitTemplate(
                "run",
                R.string.habit_template_run,
                R.string.habit_template_run_description,
                suggestedWeeklyTarget = 2
            ),
            HabitTemplate(
                "stretch",
                R.string.habit_template_stretch,
                R.string.habit_template_stretch_description
            )
        )
    ),
    HabitCategory(
        "growth",
        R.string.habit_category_growth,
        listOf(
            HabitTemplate(
                "read",
                R.string.habit_template_read,
                R.string.habit_template_read_description
            ),
            HabitTemplate(
                "learn_language",
                R.string.habit_template_learn_language,
                R.string.habit_template_learn_language_description,
                suggestedWeeklyTarget = 4
            ),
            HabitTemplate(
                "meditate",
                R.string.habit_template_meditate,
                R.string.habit_template_meditate_description
            ),
            HabitTemplate(
                "learn_skill",
                R.string.habit_template_learn_skill,
                R.string.habit_template_learn_skill_description,
                suggestedWeeklyTarget = 3
            )
        )
    ),
    HabitCategory(
        "everyday",
        R.string.habit_category_everyday,
        listOf(
            HabitTemplate(
                "tidy_up",
                R.string.habit_template_tidy_up,
                R.string.habit_template_tidy_up_description
            ),
            HabitTemplate(
                "limit_phone",
                R.string.habit_template_limit_phone,
                R.string.habit_template_limit_phone_description
            ),
            HabitTemplate(
                "plan_day",
                R.string.habit_template_plan_day,
                R.string.habit_template_plan_day_description
            ),
            HabitTemplate(
                "journal",
                R.string.habit_template_journal,
                R.string.habit_template_journal_description
            )
        )
    )
)
