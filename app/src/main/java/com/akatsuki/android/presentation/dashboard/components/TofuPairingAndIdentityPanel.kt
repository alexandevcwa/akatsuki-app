package com.akatsuki.android.presentation.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.components.BodyText
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.CyberPanel
import com.akatsuki.android.core.designsystem.components.CyberStatusBadge
import com.akatsuki.android.core.designsystem.components.DisplayXL
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.infrastructure.network.AndroidNsdPublisher
import com.akatsuki.android.presentation.dashboard.ByteFormatUtils
import com.akatsuki.android.presentation.dashboard.HudUiState

@Composable
fun TofuPairingAndIdentityPanel(
    state: HudUiState,
    onRotatePin: () -> Unit,
    onShowQrModal: () -> Unit,
    onRegenerateCert: () -> Unit,
    onCopyText: (String, String) -> Unit
) {
    CyberPanel(
        title = "CONEXIÓN SEGURA",
        subtitle = "EMPAREJAMIENTO Y TLS",
        headerIcon = Icons.Default.Lock,
        accentColor = AkatsukiPalette.AkatsukiCrimson,
        headerTrailing = {
            CyberStatusBadge(
                label = if (state.isMdnsPublished) "mDNS ACTIVO" else "mDNS STANDBY",
                accentColor = if (state.isMdnsPublished) {
                    AkatsukiPalette.RinneganCyberCyan
                } else {
                    AkatsukiPalette.HiddenRainAsh
                }
            )
        }
    ) {
        // Fila de métricas de red (IP:Puerto + Servicio NSD con exactamente el mismo alto)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryDataCard(
                label = "SERVIDOR HTTPS",
                value = state.httpsEndpoint,
                valueColor = AkatsukiPalette.NeonTelemetry,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCopyText("Endpoint HTTPS", state.httpsEndpoint) }
            )
            TelemetryDataCard(
                label = "mDNS (UDP ${AndroidNsdPublisher.MDNS_STANDARD_PORT})",
                value = AndroidNsdPublisher.SERVICE_TYPE_FQDN,
                valueColor = AkatsukiPalette.RinneganCyberCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bloque central del PIN Efímero de 6 dígitos (DisplayXL 32.sp Orbitron Bold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cyberChamferSurface(
                    chamfer = 14.dp,
                    backgroundColor = AkatsukiPalette.ShadowMantle,
                    borderColor = if (state.pinLockedRemainingSeconds > 0) {
                        AkatsukiPalette.MangekyoScarlet
                    } else {
                        AkatsukiPalette.AkatsukiCrimson.copy(alpha = 0.75f)
                    },
                    borderWidth = 1.5.dp
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListHeaderText(
                    text = "PIN DE ACCESO",
                    color = AkatsukiPalette.CrimsonHighlight
                )
                MonoTelemetry(
                    text = if (state.pinLockedRemainingSeconds > 0) {
                        "BLOQUEADO: ${state.pinLockedRemainingSeconds}s"
                    } else {
                        "EXPIRA EN ${ByteFormatUtils.formatDurationMmSs(state.pinRemainingSeconds)}"
                    },
                    color = if (state.pinLockedRemainingSeconds > 0) {
                        AkatsukiPalette.MangekyoScarlet
                    } else {
                        AkatsukiPalette.NeonTelemetry
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            DisplayXL(
                text = state.pinCode.chunked(3).joinToString(" "),
                color = if (state.pinLockedRemainingSeconds > 0) {
                    AkatsukiPalette.MangekyoScarlet
                } else {
                    AkatsukiPalette.CloudWhite
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            BodyText(
                text = if (state.pinFailedAttempts > 0) {
                    "Intentos fallidos registrados: ${state.pinFailedAttempts}/5 (Bloqueo 60s al 5º intento)"
                } else {
                    "Ingresa este PIN en Akatsuki Desktop."
                },
                color = if (state.pinFailedAttempts > 0) {
                    AkatsukiPalette.PaperBombAmber
                } else {
                    AkatsukiPalette.HiddenRainAsh
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberButton(
                    text = "ROTAR PIN",
                    onClick = onRotatePin,
                    variant = CyberButtonVariant.Secondary,
                    size = CyberButtonSize.Sm,
                    leadingIcon = Icons.Default.Refresh,
                    modifier = Modifier.weight(1f)
                )
                CyberButton(
                    text = "CÓDIGO QR TOFU",
                    onClick = onShowQrModal,
                    variant = CyberButtonVariant.Primary,
                    size = CyberButtonSize.Sm,
                    leadingIcon = Icons.Default.QrCode2,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tarjeta simétrica del Certificado X.509 y Huella SHA-256 (misma jerarquía visual que el PIN)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cyberChamferSurface(
                    chamfer = 14.dp,
                    backgroundColor = AkatsukiPalette.ShadowMantle,
                    borderColor = AkatsukiPalette.SusanooViolet.copy(alpha = 0.70f),
                    borderWidth = 1.5.dp
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Fila superior simétrica (Título corto a la izquierda | Estado TLS a la derecha)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListHeaderText(
                    text = "CERTIFICADO X.509",
                    color = AkatsukiPalette.SusanooViolet
                )
                MonoTelemetry(
                    text = "SHA-256 • TLS 1.3",
                    color = AkatsukiPalette.ZetsuBioGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Recuadro central centrado con la huella dividida en 4 líneas idénticas de 8 bloques
            val formattedFingerprintRows = remember(state.tlsFingerprintSha256) {
                val bytes = state.tlsFingerprintSha256.split(':')
                if (bytes.size == 32) {
                    bytes.chunked(8).map { rowBytes -> rowBytes.joinToString(" : ") }
                } else {
                    listOf(state.tlsFingerprintSha256)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberChamferSurface(
                        chamfer = 9.dp,
                        backgroundColor = AkatsukiPalette.ObsidianCloak,
                        borderColor = AkatsukiPalette.PanelSubtleBorder,
                        borderWidth = 1.dp
                    )
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                formattedFingerprintRows.forEach { rowHex ->
                    MonoTelemetry(
                        text = rowHex,
                        color = AkatsukiPalette.CloudWhite,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BodyText(
                text = "Huella de seguridad única que verifica la identidad de este teléfono en tu PC.",
                color = AkatsukiPalette.HiddenRainAsh,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Fila inferior simétrica de acciones (idéntica proporción a la tarjeta del PIN)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberButton(
                    text = "COPIAR HUELLA",
                    onClick = {
                        onCopyText("Huella SHA-256 TLS", state.tlsFingerprintSha256)
                    },
                    variant = CyberButtonVariant.Secondary,
                    size = CyberButtonSize.Sm,
                    leadingIcon = Icons.Default.ContentCopy,
                    modifier = Modifier.weight(1f)
                )
                CyberButton(
                    text = "REGENERAR X.509",
                    onClick = onRegenerateCert,
                    variant = CyberButtonVariant.Danger,
                    size = CyberButtonSize.Sm,
                    leadingIcon = Icons.Default.Refresh,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
