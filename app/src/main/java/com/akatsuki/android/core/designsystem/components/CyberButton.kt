package com.akatsuki.android.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.core.designsystem.theme.AkatsukiTheme

/**
 * Matriz de Estilos para Botones (CyberButton Variants) — Sección 1.1.D:
 * - Primary:   Fondo #E11D48 | Texto #F8FAFC | Borde #FF1E4D (1.5px) | Hover/Press: Fondo #FF1E4D + Borde #FF1E4D
 * - Secondary: Fondo #141624 | Texto #00F0FF | Borde rgba(0, 240, 255, 0.45) | Hover/Press: Fondo rgba(0, 240, 255, 0.15) + Borde #00F0FF
 * - Ghost:     Fondo transp. | Texto #CBD5E1 | Borde transp. | Hover/Press: Fondo #1D2033 + Texto #F8FAFC + Borde rgba(255,255,255,0.28)
 * - Danger:    Fondo #4C0519 | Texto #FB7185 | Borde #E11D48 (1.5px) | Hover/Press: Fondo #FF003C + Texto #F8FAFC + Borde #FF1E4D
 */
enum class CyberButtonVariant {
    Primary,
    Secondary,
    Ghost,
    Danger
}

enum class CyberButtonSize {
    Md,
    Sm
}

@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CyberButtonVariant = CyberButtonVariant.Primary,
    size: CyberButtonSize = CyberButtonSize.Md,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetBackground = when (variant) {
        CyberButtonVariant.Primary ->
            if (isPressed) AkatsukiPalette.SharinganNeon else AkatsukiPalette.AkatsukiCrimson
        CyberButtonVariant.Secondary ->
            if (isPressed) AkatsukiPalette.SecondaryHoverBg else AkatsukiPalette.TacticalSurface
        CyberButtonVariant.Ghost ->
            if (isPressed) AkatsukiPalette.UchihaSlate else Color.Transparent
        CyberButtonVariant.Danger ->
            if (isPressed) AkatsukiPalette.MangekyoScarlet else AkatsukiPalette.DriedBlood
    }

    val targetTextColor = when (variant) {
        CyberButtonVariant.Primary -> AkatsukiPalette.CloudWhite
        CyberButtonVariant.Secondary -> AkatsukiPalette.RinneganCyberCyan
        CyberButtonVariant.Ghost ->
            if (isPressed) AkatsukiPalette.CloudWhite else AkatsukiPalette.ShinobiSilver
        CyberButtonVariant.Danger ->
            if (isPressed) AkatsukiPalette.CloudWhite else AkatsukiPalette.CrimsonHighlight
    }

    val targetBorderColor = when (variant) {
        CyberButtonVariant.Primary -> AkatsukiPalette.SharinganNeon
        CyberButtonVariant.Secondary ->
            if (isPressed) AkatsukiPalette.RinneganCyberCyan else AkatsukiPalette.SecondaryBorder
        CyberButtonVariant.Ghost ->
            if (isPressed) AkatsukiPalette.GhostHoverBorder else Color.Transparent
        CyberButtonVariant.Danger ->
            if (isPressed) AkatsukiPalette.SharinganNeon else AkatsukiPalette.AkatsukiCrimson
    }

    val bgColor by animateColorAsState(targetValue = targetBackground, label = "cyberBtnBg")
    val contentColor by animateColorAsState(targetValue = targetTextColor, label = "cyberBtnText")
    val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "cyberBtnBorder")

    val paddingValues = when (size) {
        CyberButtonSize.Md -> PaddingValues(horizontal = 18.dp, vertical = 11.dp)
        CyberButtonSize.Sm -> PaddingValues(horizontal = 12.dp, vertical = 7.dp)
    }

    val textStyle = when (size) {
        CyberButtonSize.Md -> AkatsukiTheme.typography.buttonMd
        CyberButtonSize.Sm -> AkatsukiTheme.typography.buttonSm
    }

    val iconSize = when (size) {
        CyberButtonSize.Md -> 17.dp
        CyberButtonSize.Sm -> 14.dp
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .cyberChamferSurface(
                chamfer = 9.dp, // .cyber-chamfer-sm (9px)
                backgroundColor = bgColor,
                borderColor = borderColor,
                borderWidth = 1.5.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text.uppercase(),
                style = textStyle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
