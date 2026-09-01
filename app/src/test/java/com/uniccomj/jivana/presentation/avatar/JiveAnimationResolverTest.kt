package com.uniccomj.jivana.presentation.avatar

import com.uniccomj.jivana.R
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveReaction
import com.uniccomj.jivana.domain.model.JiveSleepiness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiveAnimationResolverTest {
    private val resolver = JiveAnimationResolver()

    @Test
    fun `neutral condition resolves to looping idle`() {
        val animation = resolver.resolve(JiveCondition())

        assertEquals(R.drawable.jive_idle, animation.drawableRes)
        assertEquals(JiveAnimationPlayback.LOOP, animation.playback)
    }

    @Test
    fun `condition without dedicated asset falls back to idle`() {
        val condition = JiveCondition(
            mood = JiveMood.DEVASTATED,
            energy = JiveEnergy.EXHAUSTED,
            sleepiness = JiveSleepiness.SLEEPING,
            reaction = JiveReaction.CELEBRATING
        )

        val animation = resolver.resolve(condition)

        assertEquals(R.drawable.jive_idle, animation.drawableRes)
        assertEquals(JiveAnimationPlayback.LOOP, animation.playback)
    }

    @Test
    fun `first matching rule determines animation priority`() {
        val higherPriorityAnimation = JiveAnimation(
            drawableRes = R.drawable.jive_idle,
            playback = JiveAnimationPlayback.ONE_SHOT
        )
        val prioritizedResolver = JiveAnimationResolver(
            prioritizedRules = listOf(
                JiveAnimationRule { higherPriorityAnimation },
                JiveAnimationRule {
                    JiveAnimation(
                        drawableRes = R.drawable.jive_idle,
                        playback = JiveAnimationPlayback.LOOP
                    )
                }
            )
        )

        val animation = prioritizedResolver.resolve(JiveCondition())

        assertEquals(higherPriorityAnimation, animation)
    }
}
