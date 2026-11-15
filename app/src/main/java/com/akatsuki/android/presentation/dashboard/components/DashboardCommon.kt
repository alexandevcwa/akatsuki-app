package com.akatsuki.android.presentation.dashboard.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.components.BodyText
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.Heading3
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette

@Composable
fun TelemetryDataCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(60.dp)
            .cyberChamferSurface(
                chamfer = 9.dp,
                backgroundColor = AkatsukiPalette.ShadowMantle,
                borderColor = AkatsukiPalette.PanelSubtleBorder,
                borderWidth = 1.5.dp
            )
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        ListHeaderText(
            text = label,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        MonoTelemetry(
            text = value,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
fun HudTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CyberButton(
        text = label,
        onClick = onClick,
        variant = if (selected) CyberButtonVariant.Secondary else CyberButtonVariant.Ghost,
        size = CyberButtonSize.Sm,
        modifier = modifier
    )
}

@Composable
fun EmptyStateBox(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberChamferSurface(
                chamfer = 9.dp,
                backgroundColor = AkatsukiPalette.ShadowMantle,
                borderColor = AkatsukiPalette.PanelSubtleBorder,
                borderWidth = 1.dp
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AkatsukiPalette.HiddenRainAsh,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Heading3(
            text = title,
            color = AkatsukiPalette.CloudWhite
        )
        Spacer(modifier = Modifier.height(4.dp))
        BodyText(
            text = subtitle,
            color = AkatsukiPalette.HiddenRainAsh
        )
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copiado al portapapeles", Toast.LENGTH_SHORT).show()
}
