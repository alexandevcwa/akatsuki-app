package com.akatsuki.android.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.theme.AkatsukiCyberpunkTheme
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.presentation.dashboard.components.HudBottomStatusBar
import com.akatsuki.android.presentation.dashboard.components.HudTopHeader
import com.akatsuki.android.presentation.dashboard.components.StoragePermissionAlertPanel
import com.akatsuki.android.presentation.dashboard.components.TelemetryAndOperationsPanel
import com.akatsuki.android.presentation.dashboard.components.TofuPairingAndIdentityPanel
import com.akatsuki.android.presentation.dashboard.components.copyToClipboard
import com.akatsuki.android.presentation.dashboard.components.dialogs.NodeConfigurationDialog
import com.akatsuki.android.presentation.dashboard.components.dialogs.TofuQrCodeDialog

@Composable
fun DashboardScreen(
    state: HudUiState,
    onIntent: (HudIntent) -> Unit,
    onRequestStoragePermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AkatsukiPalette.ObsidianCloak)
            .statusBarsPadding()
    ) {
        // HEADER HUD TÁCTICO
        HudTopHeader(
            state = state,
            onToggleServer = { onIntent(HudIntent.ToggleServer) },
            onOpenSettings = { onIntent(HudIntent.SetShowConfigModal(true)) }
        )

        // Lienzo principal desplazable
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ALERTA DE PERMISO MANAGE_EXTERNAL_STORAGE (si falta)
            if (!state.hasAllFilesPermission) {
                StoragePermissionAlertPanel(
                    onGrantPermissionClick = onRequestStoragePermission,
                    onRefreshClick = { onIntent(HudIntent.RefreshNetworkAndPermissions) }
                )
            }

            // PANEL PRINCIPAL DE EMPAREJAMIENTO SEGURO TOFU + IDENTIDAD TLS 1.3
            TofuPairingAndIdentityPanel(
                state = state,
                onRotatePin = { onIntent(HudIntent.RotatePin) },
                onShowQrModal = { onIntent(HudIntent.SetShowQrModal(true)) },
                onRegenerateCert = { onIntent(HudIntent.RegenerateTlsCertificate) },
                onCopyText = { label, text ->
                    copyToClipboard(context, label, text)
                }
            )

            // PANEL DE TELEMETRÍA EN TIEMPO REAL, ALMACENAMIENTO Y SESIONES
            TelemetryAndOperationsPanel(
                state = state,
                onSelectTab = { onIntent(HudIntent.SelectTab(it)) },
                onRevokeSession = { onIntent(HudIntent.RevokeSession(it)) },
                onRevokeAllSessions = { onIntent(HudIntent.RevokeAllSessions) },
                onNavigateDir = { onIntent(HudIntent.NavigateDirectory(it)) },
                onNavigateParent = { onIntent(HudIntent.NavigateParentDirectory) },
                onClearLogs = { onIntent(HudIntent.ClearAuditLogs) }
            )
        }

        // BARRA DE ESTADO INFERIOR HUD
        HudBottomStatusBar(state = state)
    }

    if (state.showQrCodeModal) {
        TofuQrCodeDialog(
            state = state,
            onDismiss = { onIntent(HudIntent.SetShowQrModal(false)) },
            onRotatePin = { onIntent(HudIntent.RotatePin) }
        )
    }

    if (state.showNodeConfigModal) {
        NodeConfigurationDialog(
            currentName = state.deviceName,
            currentPort = state.port,
            onDismiss = { onIntent(HudIntent.SetShowConfigModal(false)) },
            onSave = { name, port ->
                onIntent(HudIntent.SaveNodeConfig(name, port))
            }
        )
    }
}

@Preview
@Composable
private fun DashboardScreenPreview() {
    AkatsukiCyberpunkTheme {
        DashboardScreen(
            state = HudUiState(),
            onIntent = {},
            onRequestStoragePermission = {}
        )
    }
}
