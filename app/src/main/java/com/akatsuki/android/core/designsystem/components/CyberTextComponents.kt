package com.akatsuki.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.core.designsystem.theme.AkatsukiTheme

/**
 * Componentes tipográficos equivalentes a la tabla de la sección 1.2:
 * - <DisplayXL />      -> text-display-xl (Orbitron 32sp Bold UPPERCASE)
 * - <Heading1 />       -> text-heading-1 (Orbitron 22sp Bold UPPERCASE)
 * - <Heading2 />       -> text-heading-2 (Orbitron 18sp SemiBold UPPERCASE)
 * - <Heading3 />       -> text-heading-3 (Rajdhani 15sp SemiBold UPPERCASE)
 * - <BodyText />       -> text-body-md (Plus Jakarta Sans 14sp Regular)
 * - <ListItemText />   -> text-list-item (Plus Jakarta Sans 13sp Medium)
 * - <ListHeaderText /> -> text-list-header (Rajdhani 12sp Bold UPPERCASE)
 * - <MonoTelemetry />  -> text-mono-data (JetBrains Mono 12sp Medium)
 * - <MonoHash />       -> text-mono-hash (JetBrains Mono 11sp Regular)
 */
@Composable
fun DisplayXL(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.CloudWhite,
    textAlign: TextAlign? = null
) {
    Text(
        text = text.uppercase(),
        style = AkatsukiTheme.typography.displayXl,
        color = color,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
fun Heading1(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.CloudWhite,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text.uppercase(),
        style = AkatsukiTheme.typography.heading1,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun Heading2(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.CloudWhite,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text.uppercase(),
        style = AkatsukiTheme.typography.heading2,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun Heading3(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.CrimsonHighlight,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text.uppercase(),
        style = AkatsukiTheme.typography.heading3,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.ShinobiSilver,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        style = AkatsukiTheme.typography.bodyMd,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
fun ListItemText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.ShinobiSilver,
    maxLines: Int = 1
) {
    Text(
        text = text,
        style = AkatsukiTheme.typography.listItem,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun ListHeaderText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.HiddenRainAsh,
    maxLines: Int = 1
) {
    Text(
        text = text.uppercase(),
        style = AkatsukiTheme.typography.listHeader,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun MonoTelemetry(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.NeonTelemetry,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = AkatsukiTheme.typography.monoData,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun MonoHash(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AkatsukiPalette.HiddenRainAsh,
    maxLines: Int = 2
) {
    Text(
        text = text,
        style = AkatsukiTheme.typography.monoHash,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Insignia de estado táctica con recorte .cyber-chamfer-sm (9.dp)
 */
@Composable
fun CyberStatusBadge(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = true
) {
    Row(
        modifier = modifier
            .cyberChamferSurface(
                chamfer = 9.dp,
                backgroundColor = accentColor.copy(alpha = 0.14f),
                borderColor = accentColor.copy(alpha = 0.65f),
                borderWidth = 1.5.dp
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label.uppercase(),
            style = AkatsukiTheme.typography.listHeader,
            color = accentColor
        )
    }
}
