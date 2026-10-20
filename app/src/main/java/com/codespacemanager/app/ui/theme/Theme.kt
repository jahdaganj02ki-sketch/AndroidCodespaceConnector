package com.codespacemanager.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppPalette.Accent,
    onPrimary = AppPalette.OnAccent,
    primaryContainer = AppPalette.AccentSubtle,
    onPrimaryContainer = AppPalette.TextPrimary,
    secondary = AppPalette.TextSecondary,
    onSecondary = AppPalette.TextPrimary,
    background = AppPalette.Canvas,
    onBackground = AppPalette.TextPrimary,
    surface = AppPalette.Surface,
    onSurface = AppPalette.TextPrimary,
    surfaceVariant = AppPalette.SurfaceRaised,
    onSurfaceVariant = AppPalette.TextSecondary,
    outline = AppPalette.Border,
    outlineVariant = AppPalette.BorderStrong,
    error = AppPalette.TextPrimary,
    onError = AppPalette.OnAccent,
    scrim = Color.Black,
)

/** Zugriff auf die exakten Design-Tokens (unabhängig von den Material-Slots). */
@Immutable
data class AppColors(
    val canvas: Color = AppPalette.Canvas,
    val surface: Color = AppPalette.Surface,
    val surfaceInset: Color = AppPalette.SurfaceInset,
    val surfaceRaised: Color = AppPalette.SurfaceRaised,
    val border: Color = AppPalette.Border,
    val borderStrong: Color = AppPalette.BorderStrong,
    val textPrimary: Color = AppPalette.TextPrimary,
    val textSecondary: Color = AppPalette.TextSecondary,
    val textMuted: Color = AppPalette.TextMuted,
    val accent: Color = AppPalette.Accent,
    val accentHover: Color = AppPalette.AccentHover,
    val accentPressed: Color = AppPalette.AccentPressed,
    val onAccent: Color = AppPalette.OnAccent,
    val accentSubtle: Color = AppPalette.AccentSubtle,
    val selection: Color = AppPalette.Selection,
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

@Composable
fun AppTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppPalette.SurfaceInset.toArgb()
            window.navigationBarColor = AppPalette.SurfaceInset.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalAppColors provides AppColors()) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
