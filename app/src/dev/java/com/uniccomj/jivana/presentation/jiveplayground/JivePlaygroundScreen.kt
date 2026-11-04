package com.uniccomj.jivana.presentation.jiveplayground

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.uniccomj.jivana.R
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness

@Composable
fun JivePlaygroundScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val animator = remember { JiveAnimator() }
    var animation by remember { mutableStateOf(JiveAnimation.IDLE) }
    var outfitId by rememberSaveable { mutableStateOf(JiveWardrobe.Default.id) }
    var conditionPreview by rememberSaveable { mutableStateOf(false) }
    var mood by rememberSaveable { mutableStateOf(JiveMood.NEUTRAL) }
    var energy by rememberSaveable { mutableStateOf(JiveEnergy.NORMAL) }
    var sleepiness by rememberSaveable { mutableStateOf(JiveSleepiness.AWAKE) }
    val outfit = JiveWardrobe.All.first { it.id == outfitId }
    val visualState = remember(conditionPreview, mood, energy, sleepiness) {
        if (conditionPreview) {
            JiveVisualStateResolver.resolve(JiveCondition(mood, energy, sleepiness))
        } else {
            JiveVisualState()
        }
    }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(visualState) {
        animator.updateVisualState(visualState)
        frame++
    }
    JiveAnimationClock(animator) {
        frame++
        if (animation != animator.animation) animation = animator.animation
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start).testTag("playground_back")
        ) {
            Text(stringResource(R.string.jive_playground_back))
        }
        Text(
            stringResource(R.string.jive_playground_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            stringResource(R.string.jive_playground_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !conditionPreview,
                onClick = { conditionPreview = false },
                label = { Text(stringResource(R.string.jive_preview_animations)) },
                modifier = Modifier.testTag("preview_animations")
            )
            FilterChip(
                selected = conditionPreview,
                onClick = {
                    conditionPreview = true
                    animator.select(JiveAnimation.IDLE)
                    animation = animator.animation
                    frame++
                },
                label = { Text(stringResource(R.string.jive_preview_condition)) },
                modifier = Modifier.testTag("preview_condition")
            )
        }
        CharacterPreview(
            pose = {
                @Suppress("UNUSED_EXPRESSION")
                frame // Read only in the draw phase, not during composition.
                animator.pose
            },
            outfit = outfit,
            animation = animation
        )
        if (conditionPreview) {
            ConditionControls(
                mood = mood,
                energy = energy,
                sleepiness = sleepiness,
                onMood = { mood = it },
                onEnergy = { energy = it },
                onSleepiness = { sleepiness = it }
            )
        } else {
            Text(
                stringResource(R.string.jive_playground_animation),
                style = MaterialTheme.typography.titleMedium
            )
            AnimationControls(animation) {
                animator.select(it)
                animation = animator.animation
                frame++
            }
        }
        Text(
            stringResource(R.string.jive_playground_outfit),
            style = MaterialTheme.typography.titleMedium
        )
        OutfitControls(outfitId) { outfitId = it }
    }
}

@Composable
private fun ConditionControls(
    mood: JiveMood,
    energy: JiveEnergy,
    sleepiness: JiveSleepiness,
    onMood: (JiveMood) -> Unit,
    onEnergy: (JiveEnergy) -> Unit,
    onSleepiness: (JiveSleepiness) -> Unit
) {
    ConditionOptions(
        R.string.jive_condition_mood,
        JiveMood.entries,
        mood,
        "mood",
        { it.label() },
        onMood
    )
    ConditionOptions(
        R.string.jive_condition_energy,
        JiveEnergy.entries,
        energy,
        "energy",
        { it.label() },
        onEnergy
    )
    ConditionOptions(
        R.string.jive_condition_sleepiness,
        JiveSleepiness.entries,
        sleepiness,
        "sleepiness",
        { it.label() },
        onSleepiness
    )
}

@Composable
private fun <T : Enum<T>> ConditionOptions(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    tag: String,
    label: (T) -> Int,
    onSelect: (T) -> Unit
) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(label(option))) },
                modifier = Modifier.testTag("${tag}_${option.name}")
            )
        }
    }
}

@StringRes
private fun JiveMood.label(): Int = when (this) {
    JiveMood.NEUTRAL -> R.string.jive_mood_neutral
    JiveMood.HAPPY -> R.string.jive_mood_happy
    JiveMood.VERY_HAPPY -> R.string.jive_mood_very_happy
    JiveMood.ECSTATIC -> R.string.jive_mood_ecstatic
    JiveMood.SAD -> R.string.jive_mood_sad
    JiveMood.VERY_SAD -> R.string.jive_mood_very_sad
    JiveMood.DEVASTATED -> R.string.jive_mood_devastated
}

@StringRes
private fun JiveEnergy.label(): Int = when (this) {
    JiveEnergy.NORMAL -> R.string.jive_energy_normal
    JiveEnergy.TIRED -> R.string.jive_energy_tired
    JiveEnergy.VERY_TIRED -> R.string.jive_energy_very_tired
    JiveEnergy.EXHAUSTED -> R.string.jive_energy_exhausted
}

@StringRes
private fun JiveSleepiness.label(): Int = when (this) {
    JiveSleepiness.AWAKE -> R.string.jive_sleepiness_awake
    JiveSleepiness.SLEEPY -> R.string.jive_sleepiness_sleepy
    JiveSleepiness.VERY_SLEEPY -> R.string.jive_sleepiness_very_sleepy
    JiveSleepiness.SLEEPING -> R.string.jive_sleepiness_sleeping
}

@Composable
private fun CharacterPreview(pose: () -> JivePose, outfit: JiveOutfit, animation: JiveAnimation) {
    val description = stringResource(R.string.jive_playground_character)
    val state = stringResource(animation.label()) + ", " + stringResource(outfit.label())
    Box(
        modifier = Modifier.fillMaxWidth().height(340.dp)
            .background(JivePalette.Background, MaterialTheme.shapes.large)
            .semantics {
                contentDescription = description
                stateDescription = state
            }.testTag("jive_character"),
        contentAlignment = Alignment.Center
    ) {
        JiveCharacter(pose = pose, outfit = outfit, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun AnimationControls(animation: JiveAnimation, onSelect: (JiveAnimation) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in JiveAnimation.entries) {
            FilterChip(
                selected = animation == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.label())) },
                modifier = Modifier.testTag("animation_${option.name}")
            )
        }
    }
}

@Composable
private fun OutfitControls(outfitId: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in JiveWardrobe.All) {
            FilterChip(
                selected = outfitId == option.id,
                onClick = { onSelect(option.id) },
                label = { Text(stringResource(option.label())) },
                modifier = Modifier.testTag("outfit_${option.id}")
            )
        }
    }
}

@StringRes
private fun JiveAnimation.label(): Int = when (this) {
    JiveAnimation.IDLE -> R.string.jive_animation_idle
    JiveAnimation.HAPPY -> R.string.jive_animation_happy
    JiveAnimation.WALKING -> R.string.jive_animation_walking
    JiveAnimation.SAD -> R.string.jive_animation_sad
}

@StringRes
private fun JiveOutfit.label(): Int = when (id) {
    JiveWardrobe.Hoodie.id -> R.string.jive_outfit_hoodie
    JiveWardrobe.Sport.id -> R.string.jive_outfit_sport
    else -> R.string.jive_outfit_default
}
