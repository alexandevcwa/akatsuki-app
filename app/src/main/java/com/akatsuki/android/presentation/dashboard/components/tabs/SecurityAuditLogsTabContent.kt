package com.akatsuki.android.presentation.dashboard.components.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.MonoHash
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.model.SecurityAuditLog

@Composable
fun SecurityAuditLogsTabContent(
    logs: List<SecurityAuditLog>,
    onClearLogs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListHeaderText(
                text = "BITÁCORA DE SEGURIDAD Y HANDSHAKES TLS",
                color = AkatsukiPalette.ShinobiSilver
            )
            CyberButton(
                text = "LIMPIAR",
                onClick = onClearLogs,
                variant = CyberButtonVariant.Ghost,
                size = CyberButtonSize.Sm
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .cyberChamferSurface(
                    chamfer = 9.dp,
                    backgroundColor = AkatsukiPalette.ObsidianCloak,
                    borderColor = AkatsukiPalette.PanelSubtleBorder,
                    borderWidth = 1.5.dp
                )
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            logs.forEach { log ->
                val tagColor = when (log.severity) {
                    AuditSeverity.INFO -> AkatsukiPalette.NeonTelemetry
                    AuditSeverity.SUCCESS -> AkatsukiPalette.ZetsuBioGreen
                    AuditSeverity.WARNING -> AkatsukiPalette.PaperBombAmber
                    AuditSeverity.CRITICAL -> AkatsukiPalette.MangekyoScarlet
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    MonoHash(
                        text = "[${log.timestamp}]",
                        color = AkatsukiPalette.HiddenRainAsh
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MonoTelemetry(
                        text = "[${log.tag}]",
                        color = tagColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MonoHash(
                        text = log.message,
                        color = AkatsukiPalette.ShinobiSilver,
                        maxLines = 3,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
