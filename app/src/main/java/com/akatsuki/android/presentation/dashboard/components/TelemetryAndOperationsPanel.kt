package com.akatsuki.android.presentation.dashboard.components

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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.components.CyberPanel
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.presentation.dashboard.ByteFormatUtils
import com.akatsuki.android.presentation.dashboard.HudTelemetryTab
import com.akatsuki.android.presentation.dashboard.HudUiState
import com.akatsuki.android.presentation.dashboard.components.tabs.ActiveSessionsTabContent
import com.akatsuki.android.presentation.dashboard.components.tabs.LocalStorageBrowserTabContent
import com.akatsuki.android.presentation.dashboard.components.tabs.SecurityAuditLogsTabContent
import com.akatsuki.android.presentation.dashboard.components.tabs.TransfersTabContent

@Composable
fun TelemetryAndOperationsPanel(
    state: HudUiState,
    onSelectTab: (HudTelemetryTab) -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeAllSessions: () -> Unit,
    onNavigateDir: (String) -> Unit,
    onNavigateParent: () -> Unit,
    onClearLogs: () -> Unit
) {
    CyberPanel(
        title = "TELEMETRÍA",
        subtitle = "INFORMACIÓN DE SERVICIOS",
        headerIcon = Icons.Default.WifiTethering,
        accentColor = AkatsukiPalette.RinneganCyberCyan
    ) {
        // Barra de capacidad de almacenamiento de Android (/storage/emulated/0)
        StorageCapacityBar(
            totalBytes = state.storageMetrics.androidStorageTotalBytes,
            freeBytes = state.storageMetrics.androidStorageFreeBytes
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Selector de pestañas tácticas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudTabButton(
                label = "FLUJO (${state.transfers.size})",
                selected = state.selectedTab == HudTelemetryTab.TRANSFERS,
                onClick = { onSelectTab(HudTelemetryTab.TRANSFERS) },
                modifier = Modifier.weight(1f)
            )
            HudTabButton(
                label = "PC (${state.activeSessions.size})",
                selected = state.selectedTab == HudTelemetryTab.SESSIONS,
                onClick = { onSelectTab(HudTelemetryTab.SESSIONS) },
                modifier = Modifier.weight(1f)
            )
            HudTabButton(
                label = "ARCHIVOS",
                selected = state.selectedTab == HudTelemetryTab.STORAGE_BROWSER,
                onClick = { onSelectTab(HudTelemetryTab.STORAGE_BROWSER) },
                modifier = Modifier.weight(1f)
            )
            HudTabButton(
                label = "LOGS (${state.auditLogs.size})",
                selected = state.selectedTab == HudTelemetryTab.AUDIT_LOGS,
                onClick = { onSelectTab(HudTelemetryTab.AUDIT_LOGS) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (state.selectedTab) {
            HudTelemetryTab.TRANSFERS -> TransfersTabContent(transfers = state.transfers)
            HudTelemetryTab.SESSIONS -> ActiveSessionsTabContent(
                sessions = state.activeSessions,
                onRevokeSession = onRevokeSession,
                onRevokeAll = onRevokeAllSessions
            )
            HudTelemetryTab.STORAGE_BROWSER -> LocalStorageBrowserTabContent(
                currentPath = state.currentBrowserPath,
                entries = state.browserEntries,
                error = state.browserError,
                onNavigateDir = onNavigateDir,
                onNavigateParent = onNavigateParent
            )
            HudTelemetryTab.AUDIT_LOGS -> SecurityAuditLogsTabContent(
                logs = state.auditLogs,
                onClearLogs = onClearLogs
            )
        }
    }
}

@Composable
private fun StorageCapacityBar(
    totalBytes: Long,
    freeBytes: Long
) {
    val safeTotal = totalBytes.coerceAtLeast(1L)
    val usedBytes = (safeTotal - freeBytes).coerceAtLeast(0L)
    val usedFraction = (usedBytes.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberChamferSurface(
                chamfer = 9.dp,
                backgroundColor = AkatsukiPalette.ShadowMantle,
                borderColor = AkatsukiPalette.PanelSubtleBorder,
                borderWidth = 1.5.dp
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = AkatsukiPalette.RinneganCyberCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                ListHeaderText(
                    text = "VOLUMEN /STORAGE/EMULATED/0",
                    color = AkatsukiPalette.CloudWhite
                )
            }
            MonoTelemetry(
                text = "LIBRE: ${ByteFormatUtils.formatBytes(freeBytes)} / ${ByteFormatUtils.formatBytes(totalBytes)}",
                color = AkatsukiPalette.NeonTelemetry
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .cyberChamferSurface(
                    chamfer = 4.dp,
                    backgroundColor = AkatsukiPalette.UchihaSlate,
                    borderColor = AkatsukiPalette.PanelSubtleBorder,
                    borderWidth = 1.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(usedFraction)
                    .height(8.dp)
                    .background(
                        if (usedFraction > 0.9f) AkatsukiPalette.MangekyoScarlet
                        else AkatsukiPalette.RinneganCyberCyan
                    )
            )
        }
    }
}
