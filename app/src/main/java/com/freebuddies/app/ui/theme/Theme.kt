package com.freebuddies.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = PrimaryVariant,
    tertiary = Accent,
    background = Primary,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onPrimary = OnDark,
    onSecondary = OnDark,
    onTertiary = Primary,
    onBackground = OnDark,
    onSurface = OnDark,
    onSurfaceVariant = OnDarkMuted,
    error = Danger,
    onError = OnDark
)

@Composable
fun FreebuddiesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
