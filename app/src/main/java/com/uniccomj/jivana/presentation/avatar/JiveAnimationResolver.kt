package com.uniccomj.jivana.presentation.avatar

import androidx.annotation.DrawableRes
import com.uniccomj.jivana.R
import com.uniccomj.jivana.domain.model.JiveCondition

enum class JiveAnimationPlayback {
    LOOP,
    ONE_SHOT
}

data class JiveAnimation(
    @param:DrawableRes val drawableRes: Int,
    val playback: JiveAnimationPlayback
)

fun interface JiveAnimationRule {
    fun resolve(condition: JiveCondition): JiveAnimation?
}

class JiveAnimationResolver(private val prioritizedRules: List<JiveAnimationRule> = DefaultRules) {
    fun resolve(condition: JiveCondition): JiveAnimation =
        prioritizedRules.firstNotNullOfOrNull { rule -> rule.resolve(condition) } ?: IdleAnimation

    private companion object {
        val DefaultRules = emptyList<JiveAnimationRule>()

        val IdleAnimation = JiveAnimation(
            drawableRes = R.drawable.jive_idle,
            playback = JiveAnimationPlayback.LOOP
        )
    }
}
