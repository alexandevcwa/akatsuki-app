package com.akatsuki.android.presentation.dashboard.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.CyberPanel
import com.akatsuki.android.core.designsystem.components.CyberQrCodeView
import com.akatsuki.android.core.designsystem.components.DisplayXL
import com.akatsuki.android.core.designsystem.components.MonoHash
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.presentation.dashboard.ByteFormatUtils
import com.akatsuki.android.presentation.dashboard.HudUiState

@Composable
fun TofuQrCodeDialog(
    state: HudUiState,
    onDismiss: () -> Unit,
    onRotatePin: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        CyberPanel(
            title = "EMPAREJAMIENTO QR // TOFU",
            subtitle = "ESCANEAR O USAR PIN EN AKATSUKI DESKTOP",
            headerIcon = Icons.Default.QrCode2,
            accentColor = AkatsukiPalette.RinneganCyberCyan,
            useGlassSurface = false
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CyberQrCodeView(
                    payload = state.qrCodePayloadJson,
                    size = 210.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                DisplayXL(
                    text = state.pinCode.chunked(3).joinToString(" "),
                    color = AkatsukiPalette.CloudWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                MonoTelemetry(
                    text = "${state.httpsEndpoint} • EXPIRA EN ${ByteFormatUtils.formatDurationMmSs(state.pinRemainingSeconds)}",
                    color = AkatsukiPalette.NeonTelemetry
                )
                Spacer(modifier = Modifier.height(8.dp))
                MonoHash(
                    text = "SHA-256: ${state.tlsFingerprintSha256}",
                    color = AkatsukiPalette.SusanooViolet,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CyberButton(
                        text = "ROTAR PIN",
                        onClick = onRotatePin,
                        variant = CyberButtonVariant.Secondary,
                        size = CyberButtonSize.Sm,
                        modifier = Modifier.weight(1f)
                    )
                    CyberButton(
                        text = "CERRAR",
                        onClick = onDismiss,
                        variant = CyberButtonVariant.Primary,
                        size = CyberButtonSize.Sm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
