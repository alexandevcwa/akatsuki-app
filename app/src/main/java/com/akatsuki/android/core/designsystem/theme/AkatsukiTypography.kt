package com.akatsuki.android.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.akatsuki.android.R

/**
 * Especificación Tipográfica Oficial "Akatsuki Cyberpunk" (Sección 1.2)
 * Familias:
 * 1. Display / Táctica (--font-display): 'Orbitron', 'Rajdhani', sans-serif
 * 2. Interfaz y Listas (--font-sans): 'Plus Jakarta Sans', system-ui, sans-serif
 * 3. Telemetría y Criptografía (--font-mono): 'JetBrains Mono', monospace
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val orbitronGoogleFont = GoogleFont("Orbitron")
private val rajdhaniGoogleFont = GoogleFont("Rajdhani")
private val plusJakartaGoogleFont = GoogleFont("Plus Jakarta Sans")
private val jetBrainsMonoGoogleFont = GoogleFont("JetBrains Mono")

val OrbitronFontFamily = FontFamily(
    Font(googleFont = orbitronGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = orbitronGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

val RajdhaniFontFamily = FontFamily(
    Font(googleFont = rajdhaniGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = rajdhaniGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(googleFont = plusJakartaGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = plusJakartaGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = plusJakartaGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(googleFont = jetBrainsMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = jetBrainsMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = jetBrainsMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

/**
 * Escala Tipográfica Exacta según tabla de la sección 1.2:
 * - text-display-xl: Orbitron 32sp, Bold (700), line-height 1.15, letter-spacing 0.08em, UPPERCASE
 * - text-heading-1: Orbitron 22sp, Bold (700), line-height 1.25, letter-spacing 0.06em, UPPERCASE
 * - text-heading-2: Orbitron/Rajdhani 18sp, SemiBold (600), line-height 1.30, letter-spacing 0.05em, UPPERCASE
 * - text-heading-3: Rajdhani 15sp, SemiBold (600), line-height 1.40, letter-spacing 0.04em, UPPERCASE
 * - text-body-md: Plus Jakarta Sans 14sp, Regular (400), line-height 1.50, letter-spacing 0em, Normal
 * - text-list-item: Plus Jakarta Sans 13sp, Medium (500), line-height 1.40, letter-spacing 0.01em, Normal
 * - text-list-header: Rajdhani 12sp, Bold (700), line-height 1.20, letter-spacing 0.08em, UPPERCASE
 * - text-button-md: Rajdhani 14sp, Bold (700), line-height 1.00, letter-spacing 0.06em, UPPERCASE
 * - text-button-sm: Rajdhani 12sp, SemiBold (600), line-height 1.00, letter-spacing 0.05em, UPPERCASE
 * - text-mono-data: JetBrains Mono 12sp, Medium (500), line-height 1.40, letter-spacing -0.01em, Normal
 * - text-mono-hash: JetBrains Mono 11sp, Regular (400), line-height 1.35, letter-spacing 0em, Normal
 */
@Immutable
data class AkatsukiTypographyScale(
    val displayXl: TextStyle = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (32f * 1.15f).sp,
        letterSpacing = (32f * 0.08f).sp,
        color = AkatsukiPalette.CloudWhite
    ),
    val heading1: TextStyle = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (22f * 1.25f).sp,
        letterSpacing = (22f * 0.06f).sp,
        color = AkatsukiPalette.CloudWhite
    ),
    val heading2: TextStyle = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = (18f * 1.30f).sp,
        letterSpacing = (18f * 0.05f).sp,
        color = AkatsukiPalette.CloudWhite
    ),
    val heading3: TextStyle = TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = (15f * 1.40f).sp,
        letterSpacing = (15f * 0.04f).sp,
        color = AkatsukiPalette.CrimsonHighlight
    ),
    val bodyMd: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (14f * 1.50f).sp,
        letterSpacing = 0.sp,
        color = AkatsukiPalette.ShinobiSilver
    ),
    val listItem: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = (13f * 1.40f).sp,
        letterSpacing = (13f * 0.01f).sp,
        color = AkatsukiPalette.ShinobiSilver
    ),
    val listHeader: TextStyle = TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (12f * 1.20f).sp,
        letterSpacing = (12f * 0.08f).sp,
        color = AkatsukiPalette.HiddenRainAsh
    ),
    val buttonMd: TextStyle = TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 14.sp,
        letterSpacing = (14f * 0.06f).sp
    ),
    val buttonSm: TextStyle = TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 12.sp,
        letterSpacing = (12f * 0.05f).sp
    ),
    val monoData: TextStyle = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = (12f * 1.40f).sp,
        letterSpacing = (-0.12).sp,
        color = AkatsukiPalette.NeonTelemetry
    ),
    val monoHash: TextStyle = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (11f * 1.35f).sp,
        letterSpacing = 0.sp,
        color = AkatsukiPalette.HiddenRainAsh
    )
)

val LocalAkatsukiTypography = staticCompositionLocalOf { AkatsukiTypographyScale() }
