package com.akatsuki.android.presentation.dashboard.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.components.CyberStatusBadge
import com.akatsuki.android.core.designsystem.components.ListItemText
import com.akatsuki.android.core.designsystem.components.MonoHash
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.domain.model.TransferDirection
import com.akatsuki.android.domain.model.TransferStatus
import com.akatsuki.android.domain.model.TransferTelemetryItem
import com.akatsuki.android.presentation.dashboard.ByteFormatUtils
import com.akatsuki.android.presentation.dashboard.components.EmptyStateBox

@Composable
fun TransfersTabContent(
    transfers: List<TransferTelemetryItem>
) {
    if (transfers.isEmpty()) {
        EmptyStateBox(
            icon = Icons.Default.SwapVert,
            title = "SIN TRANSFERENCIAS EN CURSO",
            subtitle = "Las subidas (POST /api/v1/files/upload) y descargas (GET /api/v1/files/download) mostrarán su progreso y verificación SHA-256 aquí en tiempo real."
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        transfers.forEach { item ->
            val progressFraction = if (item.totalBytes > 0L) {
                (item.bytesTransferred.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            val statusColor = when (item.status) {
                TransferStatus.IN_PROGRESS -> AkatsukiPalette.RinneganCyberCyan
                TransferStatus.COMPLETED_VERIFIED -> AkatsukiPalette.ZetsuBioGreen
                TransferStatus.FAILED_INTEGRITY, TransferStatus.FAILED_IO -> AkatsukiPalette.MangekyoScarlet
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberChamferSurface(
                        chamfer = 9.dp,
                        backgroundColor = AkatsukiPalette.ShadowMantle,
                        borderColor = statusColor.copy(alpha = 0.55f),
                        borderWidth = 1.5.dp
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (item.direction == TransferDirection.UPLOAD_PC_TO_ANDROID) {
                                Icons.Default.CloudDownload
                            } else {
                                Icons.Default.CloudUpload
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ListItemText(
                            text = item.fileName,
                            color = AkatsukiPalette.CloudWhite
                        )
                    }

                    val statusBadgeText = when (item.status) {
                        TransferStatus.IN_PROGRESS -> String.format(java.util.Locale.US, "%.1f MB/s", item.speedMbps)
                        TransferStatus.COMPLETED_VERIFIED -> "SHA-256 VERIFIED"
                        TransferStatus.FAILED_INTEGRITY -> "HASH INVALID (422)"
                        TransferStatus.FAILED_IO -> "IO ERROR"
                    }
                    CyberStatusBadge(
                        label = statusBadgeText,
                        accentColor = statusColor,
                        showDot = item.status == TransferStatus.IN_PROGRESS
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MonoHash(
                        text = item.remotePath,
                        color = AkatsukiPalette.HiddenRainAsh,
                        maxLines = 1
                    )
                    MonoTelemetry(
                        text = "${ByteFormatUtils.formatBytes(item.bytesTransferred)} / ${ByteFormatUtils.formatBytes(item.totalBytes)}",
                        color = AkatsukiPalette.NeonTelemetry
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(AkatsukiPalette.UchihaSlate)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(5.dp)
                            .background(statusColor)
                    )
                }

                if (!item.computedSha256.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    MonoHash(
                        text = "SHA-256: ${item.computedSha256}",
                        color = AkatsukiPalette.SusanooViolet,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
