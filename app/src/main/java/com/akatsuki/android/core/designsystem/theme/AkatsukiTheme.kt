package com.akatsuki.android.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val MaterialDarkColors = darkColorScheme(
    primary = AkatsukiPalette.AkatsukiCrimson,
    onPrimary = AkatsukiPalette.CloudWhite,
    primaryContainer = AkatsukiPalette.DriedBlood,
    onPrimaryContainer = AkatsukiPalette.CrimsonHighlight,
    secondary = AkatsukiPalette.RinneganCyberCyan,
    onSecondary = AkatsukiPalette.ObsidianCloak,
    tertiary = AkatsukiPalette.SusanooViolet,
    background = AkatsukiPalette.ObsidianCloak,
    onBackground = AkatsukiPalette.ShinobiSilver,
    surface = AkatsukiPalette.TacticalSurface,
    onSurface = AkatsukiPalette.CloudWhite,
    surfaceVariant = AkatsukiPalette.UchihaSlate,
    onSurfaceVariant = AkatsukiPalette.ShinobiSilver,
    error = AkatsukiPalette.MangekyoScarlet,
    onError = AkatsukiPalette.CloudWhite
)

object AkatsukiTheme {
    val colors: AkatsukiColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAkatsukiColors.current

    val typography: AkatsukiTypographyScale
        @Composable
        @ReadOnlyComposable
        get() = LocalAkatsukiTypography.current
}

@Composable
fun AkatsukiCyberpunkTheme(
    content: @Composable () -> Unit
) {
    val akatsukiColors = AkatsukiColorScheme()
    val akatsukiTypography = AkatsukiTypographyScale()

    CompositionLocalProvider(
        LocalAkatsukiColors provides akatsukiColors,
        LocalAkatsukiTypography provides akatsukiTypography
    ) {
        MaterialTheme(
            colorScheme = MaterialDarkColors,
            content = content
        )
    }
}
