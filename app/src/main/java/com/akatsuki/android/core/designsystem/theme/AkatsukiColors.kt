package com.akatsuki.android.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta de Colores Oficial del Sistema de Diseño "Akatsuki Cyberpunk"
 * Especificación maestra sección 1.1:
 * - A. Fondos y Superficies (Backgrounds & Surfaces)
 * - B. Colores de Marca y Acentos Cyberpunk (Brand & Telemetry Accents)
 * - C. Colores Tipográficos (Typography Colors)
 */
object AkatsukiPalette {
    // A. Fondos y Superficies (Backgrounds & Surfaces)
    /** --ak-bg-void (#07070C): Fondo raíz de la aplicación / Lienzo principal */
    val ObsidianCloak = Color(0xFF07070C)

    /** --ak-bg-mantle (#0D0E17): Header HUD, barra de estado inferior, fondos de inputs y subpaneles */
    val ShadowMantle = Color(0xFF0D0E17)

    /** --ak-bg-panel (#141624): Contenedores principales de paneles (CyberPanel), tarjetas y modales */
    val TacticalSurface = Color(0xFF141624)

    /** --ak-bg-elevated (#1D2033): Estado hover/seleccionado en filas de archivos e inputs en foco */
    val UchihaSlate = Color(0xFF1D2033)

    /** --ak-bg-glass (rgba(20, 22, 36, 0.78)): Superficies translúcidas y modales con desenfoque */
    val AmaterasuGlass = Color(0xC7141624)

    // B. Colores de Marca y Acentos Cyberpunk (Brand & Telemetry Accents)
    /** --ak-primary (#E11D48): Acción primaria, nubes Akatsuki, barra superior de acento en paneles locales */
    val AkatsukiCrimson = Color(0xFFE11D48)

    /** --ak-primary-glow (#FF1E4D): Estado hover de botones primarios, bordes iluminados e iconos críticos */
    val SharinganNeon = Color(0xFFFF1E4D)

    /** --ak-primary-muted (#4C0519): Fondo de filas seleccionadas, insignias carmesí y base de botones destructivos */
    val DriedBlood = Color(0xFF4C0519)

    /** --ak-cyber-cyan (#00F0FF): Telemetría en tiempo real, panel remoto Android, botones secundarios y mDNS */
    val RinneganCyberCyan = Color(0xFF00F0FF)

    /** --ak-cyber-purple (#A855F7): Indicadores criptográficos (TLS 1.3 Pinning, verificación SHA-256) */
    val SusanooViolet = Color(0xFFA855F7)

    /** --ak-status-success (#10B981): Enlace activo con Android, transferencia completada y hash verificado */
    val ZetsuBioGreen = Color(0xFF10B981)

    /** --ak-status-warning (#F59E0B): Estado sin conexión, espera de confirmación TOFU o archivos comprimidos */
    val PaperBombAmber = Color(0xFFF59E0B)

    /** --ak-status-danger (#FF003C): Errores de red/integridad, desconexión y cancelación de transferencias */
    val MangekyoScarlet = Color(0xFFFF003C)

    // C. Colores Tipográficos (Typography Colors)
    /** --ak-text-heading (#F8FAFC): Títulos (H1, H2, H3), nombres de carpetas y texto de botones primarios */
    val CloudWhite = Color(0xFFF8FAFC)

    /** --ak-text-body (#CBD5E1): Cuerpo de texto general y nombres de archivos en el explorador */
    val ShinobiSilver = Color(0xFFCBD5E1)

    /** --ak-text-muted (#64748B): Rutas de directorios, fechas de modificación, hashes secundarios y etiquetas */
    val HiddenRainAsh = Color(0xFF64748B)

    /** --ak-text-accent (#FB7185): Subtítulos de sección (Heading3), estado del footer e insignias rojas */
    val CrimsonHighlight = Color(0xFFFB7185)

    /** --ak-text-cyber (#67E8F9): Direcciones IP:Puerto, velocidades MB/s y tamaños de archivo (KB/MB/GB) */
    val NeonTelemetry = Color(0xFF67E8F9)

    // Variantes específicas de bordes para botones (Sección 1.1.D)
    val SecondaryBorder = Color(0x7300F0FF)      // rgba(0, 240, 255, 0.45)
    val SecondaryHoverBg = Color(0x2600F0FF)     // rgba(0, 240, 255, 0.15)
    val GhostHoverBorder = Color(0x47FFFFFF)     // rgba(255, 255, 255, 0.28)
    val PanelSubtleBorder = Color(0xFF23273E)    // Borde sutil por defecto para paneles tácticos
}

@Immutable
data class AkatsukiColorScheme(
    val bgVoid: Color = AkatsukiPalette.ObsidianCloak,
    val bgMantle: Color = AkatsukiPalette.ShadowMantle,
    val bgPanel: Color = AkatsukiPalette.TacticalSurface,
    val bgElevated: Color = AkatsukiPalette.UchihaSlate,
    val bgGlass: Color = AkatsukiPalette.AmaterasuGlass,
    val primary: Color = AkatsukiPalette.AkatsukiCrimson,
    val primaryGlow: Color = AkatsukiPalette.SharinganNeon,
    val primaryMuted: Color = AkatsukiPalette.DriedBlood,
    val cyberCyan: Color = AkatsukiPalette.RinneganCyberCyan,
    val cyberPurple: Color = AkatsukiPalette.SusanooViolet,
    val statusSuccess: Color = AkatsukiPalette.ZetsuBioGreen,
    val statusWarning: Color = AkatsukiPalette.PaperBombAmber,
    val statusDanger: Color = AkatsukiPalette.MangekyoScarlet,
    val textHeading: Color = AkatsukiPalette.CloudWhite,
    val textBody: Color = AkatsukiPalette.ShinobiSilver,
    val textMuted: Color = AkatsukiPalette.HiddenRainAsh,
    val textAccent: Color = AkatsukiPalette.CrimsonHighlight,
    val textCyber: Color = AkatsukiPalette.NeonTelemetry
)

val LocalAkatsukiColors = staticCompositionLocalOf { AkatsukiColorScheme() }
