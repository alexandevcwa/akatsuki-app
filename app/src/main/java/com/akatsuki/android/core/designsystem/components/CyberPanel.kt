package com.akatsuki.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette

/**
 * Contenedor principal de paneles tácticos (CyberPanel) — Sección 1.1.A y 1.3
 * - Usa fondo TacticalSurface (#141624) o AmaterasuGlass (rgba(20, 22, 36, 0.78))
 * - Geometría .cyber-chamfer-md (recorte de 14.dp en esquina superior derecha e inferior izquierda)
 * - Borde poligonal continuo de 6 lados de 1.5.dp
 * - Barra superior de acento (AkatsukiCrimson para nodos locales / RinneganCyberCyan para telemetría)
 */
@Composable
fun CyberPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    headerIcon: ImageVector? = null,
    accentColor: Color = AkatsukiPalette.AkatsukiCrimson,
    borderColor: Color = AkatsukiPalette.PanelSubtleBorder,
    useGlassSurface: Boolean = false,
    chamfer: Dp = 14.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaceBg = if (useGlassSurface) {
        AkatsukiPalette.AmaterasuGlass
    } else {
        AkatsukiPalette.TacticalSurface
    }

    Column(
        modifier = modifier.cyberChamferSurface(
            chamfer = chamfer,
            backgroundColor = surfaceBg,
            borderColor = borderColor,
            borderWidth = 1.5.dp,
            topAccentColor = accentColor,
            topAccentHeight = 2.5.dp
        )
    ) {
        if (title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AkatsukiPalette.ShadowMantle.copy(alpha = 0.72f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (headerIcon != null) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Heading2(
                            text = title,
                            maxLines = 1
                        )
                        if (subtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Heading3(
                                text = subtitle,
                                color = accentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (headerTrailing != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box { headerTrailing() }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(borderColor)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}
