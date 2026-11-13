package com.akatsuki.android.core.designsystem.shape

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Geometría de Bordes Poligonales HUD (.cyber-chamfer-*) — Sección 1.3
 *
 * Para evitar que los recortes a 45° pierdan el borde de color al usar clip-path,
 * el sistema implementa un polígono continuo de 6 lados (1.5.dp de grosor constante
 * en rectas y diagonales):
 * - CyberChamferSm: Recorte de 9.dp en la esquina superior derecha e inferior izquierda
 * - CyberChamferMd: Recorte de 14.dp en la esquina superior derecha e inferior izquierda
 */
class CyberChamferShape(val chamferSize: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cutPx = with(density) { chamferSize.toPx() }
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        return Outline.Generic(buildCyberChamferPath(size, cutPx))
    }

    companion object {
        val Sm = CyberChamferShape(9.dp)
        val Md = CyberChamferShape(14.dp)

        fun buildCyberChamferPath(size: Size, cutPx: Float): Path {
            return Path().apply {
                // Vértice 1: Superior Izquierdo (90°)
                moveTo(0f, 0f)
                // Vértice 2: Superior Derecho antes del bisel a 45°
                lineTo((size.width - cutPx).coerceAtLeast(0f), 0f)
                // Vértice 3: Superior Derecho después del bisel a 45°
                lineTo(size.width, cutPx)
                // Vértice 4: Inferior Derecho (90°)
                lineTo(size.width, size.height)
                // Vértice 5: Inferior Izquierdo antes del bisel a 45°
                lineTo(cutPx, size.height)
                // Vértice 6: Inferior Izquierdo después del bisel a 45°
                lineTo(0f, (size.height - cutPx).coerceAtLeast(0f))
                close()
            }
        }
    }
}

/**
 * Modificador que dibuja la superficie poligonal de 6 lados con relleno, barra superior
 * táctica opcional y borde continuo de 1.5.dp en rectas y diagonales a 45°.
 */
fun Modifier.cyberChamferSurface(
    chamfer: Dp = 14.dp,
    backgroundColor: Color,
    borderColor: Color,
    borderWidth: Dp = 1.5.dp,
    topAccentColor: Color? = null,
    topAccentHeight: Dp = 2.5.dp
): Modifier = this
    .clip(CyberChamferShape(chamfer))
    .drawWithCache {
        val cutPx = chamfer.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
        val strokePx = borderWidth.toPx()
        val accentHeightPx = topAccentHeight.toPx()
        val polygonPath = CyberChamferShape.buildCyberChamferPath(size, cutPx)

        onDrawWithContent {
            // 1. Fondo poligonal
            drawPath(path = polygonPath, color = backgroundColor)

            // 2. Contenido interno del componente
            drawContent()

            // 3. Barra superior de acento táctico (si aplica)
            if (topAccentColor != null && size.width > cutPx) {
                drawLine(
                    color = topAccentColor,
                    start = Offset(0f, accentHeightPx / 2f),
                    end = Offset(size.width - cutPx, accentHeightPx / 2f),
                    strokeWidth = accentHeightPx
                )
            }

            // 4. Borde poligonal continuo de 6 lados (1.5.dp constante en rectas y diagonales)
            if (borderColor != Color.Transparent && strokePx > 0f) {
                drawPath(
                    path = polygonPath,
                    color = borderColor,
                    style = Stroke(width = strokePx)
                )
            }
        }
    }
