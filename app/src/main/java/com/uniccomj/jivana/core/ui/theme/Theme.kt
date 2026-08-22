package com.uniccomj.jivana.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val JivanaColorScheme = lightColorScheme(
    primary = JivanaPrimary,
    onPrimary = JivanaOnPrimary,
    primaryContainer = JivanaPrimaryContainer,
    onPrimaryContainer = JivanaOnPrimaryContainer,
    secondary = JivanaSecondary,
    onSecondary = JivanaOnSecondary,
    secondaryContainer = JivanaSecondaryContainer,
    onSecondaryContainer = JivanaOnSecondaryContainer,
    tertiary = JivanaTertiary,
    onTertiary = JivanaOnTertiary,
    tertiaryContainer = JivanaTertiaryContainer,
    onTertiaryContainer = JivanaOnTertiaryContainer,
    background = JivanaBackground,
    onBackground = JivanaOnBackground,
    surface = JivanaSurface,
    onSurface = JivanaOnSurface,
    surfaceVariant = JivanaSurfaceVariant,
    onSurfaceVariant = JivanaOnSurfaceVariant,
    error = JivanaError,
    onError = JivanaOnError,
    outline = JivanaOutline
)

@Composable
fun JivanaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JivanaColorScheme,
        typography = JivanaTypography,
        shapes = JivanaShapes,
        content = content
    )
}
