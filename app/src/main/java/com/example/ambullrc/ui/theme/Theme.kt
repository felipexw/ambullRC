package com.example.ambullrc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Fixed dark palette matching the feature 005 design handoff — no light theme, no dynamic
// (Material You) color: the design has exactly one appearance, and honoring per-device wallpaper
// color would undermine the redesign's "one consistent visual language" goal (see
// specs/005-home-screen-ux-redesign/research.md Decision 1).
private val AmbullRCColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Background,
    onBackground = OnSurface,
    surface = SurfaceAppBar,
    onSurface = OnSurface,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = DisconnectedDot,
    onError = OnDisconnectedContainer,
    errorContainer = DisconnectedContainer,
    onErrorContainer = OnDisconnectedContainer,
)

@Composable
fun AmbullRCTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmbullRCColorScheme,
        typography = Typography,
        content = content
    )
}
