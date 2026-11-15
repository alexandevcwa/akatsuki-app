package com.akatsuki.android.presentation.dashboard.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.akatsuki.android.R
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.Heading1
import com.akatsuki.android.core.designsystem.components.Heading3
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.domain.model.ServerLifecycleState
import com.akatsuki.android.presentation.dashboard.HudUiState

@Composable
fun HudTopHeader(
    state: HudUiState,
    onToggleServer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val (statusLabel, statusColor) = when (state.serverState) {
        is ServerLifecycleState.Running -> "ONLINE • TLS 1.3" to AkatsukiPalette.ZetsuBioGreen
        is ServerLifecycleState.Starting -> "INICIANDO..." to AkatsukiPalette.RinneganCyberCyan
        is ServerLifecycleState.Error -> "ERROR DE RED" to AkatsukiPalette.MangekyoScarlet
        is ServerLifecycleState.Stopped -> "STANDBY • OFFLINE" to AkatsukiPalette.PaperBombAmber
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AkatsukiPalette.ShadowMantle)
    ) {
        // FILA 1: Identidad de Marca
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo de la nube de Akatsuki sobresaliente
                Box(
                    modifier = Modifier
                        .size(width = 76.dp, height = 52.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        AkatsukiPalette.AkatsukiCrimson.copy(alpha = 0.42f),
                                        AkatsukiPalette.DriedBlood.copy(alpha = 0.20f),
                                        Color.Transparent
                                    ),
                                    radius = size.maxDimension * 0.68f
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Sombra difuminada roja siguiendo la silueta exacta de la nube (SharinganNeon / AkatsukiCrimson)
                    Image(
                        painter = painterResource(id = R.drawable.akatsuki_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(AkatsukiPalette.SharinganNeon.copy(alpha = 0.80f)),
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(
                                radius = 9.dp,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded
                            )
                    )
                    // Nube Akatsuki principal nítida y sobresaliente
                    Image(
                        painter = painterResource(id = R.drawable.akatsuki_logo),
                        contentDescription = "Logo Akatsuki",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Heading1(
                        text = "AKATSUKI",
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Heading3(
                        text = state.deviceName,
                        color = AkatsukiPalette.CrimsonHighlight,
                        maxLines = 1
                    )
                }
            }

            CyberButton(
                text = "CFG",
                onClick = onOpenSettings,
                variant = CyberButtonVariant.Secondary,
                size = CyberButtonSize.Sm,
                leadingIcon = Icons.Default.Settings
            )
        }

        // Separador sutil entre identidad y barra de control
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(AkatsukiPalette.PanelSubtleBorder)
        )

        // FILA 2: Barra de Estado y Control del Servidor
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    ListHeaderText(
                        text = statusLabel,
                        color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                MonoTelemetry(
                    text = state.httpsEndpoint,
                    color = if (state.isServerRunning) {
                        AkatsukiPalette.NeonTelemetry
                    } else {
                        AkatsukiPalette.HiddenRainAsh
                    },
                    maxLines = 1
                )
            }

            CyberButton(
                text = if (state.isServerRunning) "DETENER LINK" else "INICIAR LINK",
                onClick = onToggleServer,
                variant = if (state.isServerRunning) CyberButtonVariant.Danger else CyberButtonVariant.Primary,
                size = CyberButtonSize.Md,
                leadingIcon = Icons.Default.PowerSettingsNew,
                modifier = Modifier.width(162.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    if (state.isServerRunning) AkatsukiPalette.AkatsukiCrimson
                    else AkatsukiPalette.PanelSubtleBorder
                )
        )
    }
}
