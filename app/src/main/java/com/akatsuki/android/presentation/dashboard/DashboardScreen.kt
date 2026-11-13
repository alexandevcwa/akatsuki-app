package com.akatsuki.android.presentation.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.akatsuki.android.R
import com.akatsuki.android.core.designsystem.components.BodyText
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.CyberPanel
import com.akatsuki.android.core.designsystem.components.CyberQrCodeView
import com.akatsuki.android.core.designsystem.components.CyberStatusBadge
import com.akatsuki.android.core.designsystem.components.DisplayXL
import com.akatsuki.android.core.designsystem.components.Heading1
import com.akatsuki.android.core.designsystem.components.Heading2
import com.akatsuki.android.core.designsystem.components.Heading3
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.ListItemText
import com.akatsuki.android.core.designsystem.components.MonoHash
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.core.designsystem.theme.AkatsukiTheme
import com.akatsuki.android.domain.model.ActiveSession
import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.model.FileEntry
import com.akatsuki.android.domain.model.SecurityAuditLog
import com.akatsuki.android.domain.model.ServerLifecycleState
import com.akatsuki.android.domain.model.TransferDirection
import com.akatsuki.android.domain.model.TransferStatus
import com.akatsuki.android.domain.model.TransferTelemetryItem

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
        // 1. HEADER HUD TÁCTICO (ShadowMantle #0D0E17)
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
            // 2. ALERTA DE PERMISO MANAGE_EXTERNAL_STORAGE (si falta)
            if (!state.hasAllFilesPermission) {
                StoragePermissionAlertPanel(
                    onGrantPermissionClick = onRequestStoragePermission,
                    onRefreshClick = { onIntent(HudIntent.RefreshNetworkAndPermissions) }
                )
            }

            // 3. PANEL PRINCIPAL DE EMPAREJAMIENTO SEGURO TOFU + IDENTIDAD TLS 1.3
            TofuPairingAndIdentityPanel(
                state = state,
                onRotatePin = { onIntent(HudIntent.RotatePin) },
                onShowQrModal = { onIntent(HudIntent.SetShowQrModal(true)) },
                onRegenerateCert = { onIntent(HudIntent.RegenerateTlsCertificate) },
                onCopyText = { label, text ->
                    copyToClipboard(context, label, text)
                }
            )

            // 4. PANEL DE TELEMETRÍA EN TIEMPO REAL, ALMACENAMIENTO Y SESIONES
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

        // 5. BARRA DE ESTADO INFERIOR HUD (ShadowMantle #0D0E17)
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

@Composable
private fun HudTopHeader(
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
        // FILA 1: Identidad de Marca (Altura fija 66.dp — nunca cambia de tamaño)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
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
                Box(
                    modifier = Modifier
                        .size(width = 62.dp, height = 44.dp)
                        .cyberChamferSurface(
                            chamfer = 8.dp,
                            backgroundColor = AkatsukiPalette.TacticalSurface,
                            borderColor = AkatsukiPalette.AkatsukiCrimson.copy(alpha = 0.85f),
                            borderWidth = 1.5.dp
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
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
                        text = "AKATSUKI // LINK",
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

        // FILA 2: Barra de Estado y Control del Servidor (Altura fija 58.dp — nunca cambia de tamaño)
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

@Composable
private fun StoragePermissionAlertPanel(
    onGrantPermissionClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    CyberPanel(
        title = "ACCESO AL ALMACENAMIENTO REQUERIDO",
        subtitle = "PERMISO MANAGE_EXTERNAL_STORAGE (/STORAGE/EMULATED/0)",
        headerIcon = Icons.Default.WarningAmber,
        accentColor = AkatsukiPalette.PaperBombAmber,
        borderColor = AkatsukiPalette.PaperBombAmber.copy(alpha = 0.65f)
    ) {
        BodyText(
            text = "Para que el cliente Akatsuki Desktop pueda explorar, descargar y subir archivos en /storage/emulated/0, Android 11+ requiere autorizar el acceso completo a archivos para este nodo."
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CyberButton(
                text = "AUTORIZAR ACCESO A ARCHIVOS",
                onClick = onGrantPermissionClick,
                variant = CyberButtonVariant.Primary,
                size = CyberButtonSize.Md,
                modifier = Modifier.weight(1f)
            )
            CyberButton(
                text = "VERIFICAR",
                onClick = onRefreshClick,
                variant = CyberButtonVariant.Secondary,
                size = CyberButtonSize.Md
            )
        }
    }
}

@Composable
private fun TofuPairingAndIdentityPanel(
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
                label = "SERVICIO mDNS",
                value = "_akatsuki._tcp. (${state.port})",
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
                    "Ingresa este PIN en Akatsuki Desktop o escanea el código QR para conectar."
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

@Composable
private fun TelemetryAndOperationsPanel(
    state: HudUiState,
    onSelectTab: (HudTelemetryTab) -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeAllSessions: () -> Unit,
    onNavigateDir: (String) -> Unit,
    onNavigateParent: () -> Unit,
    onClearLogs: () -> Unit
) {
    CyberPanel(
        title = "TELEMETRÍA EN TIEMPO REAL & OPERACIONES",
        subtitle = "FLUJO BINARIO 64KB • VERIFICACIÓN SHA-256 • SESIONES ACTIVAS",
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

@Composable
private fun TransfersTabContent(
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
                        TransferStatus.IN_PROGRESS -> String.format("%.1f MB/s", item.speedMbps)
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

@Composable
private fun ActiveSessionsTabContent(
    sessions: List<ActiveSession>,
    onRevokeSession: (String) -> Unit,
    onRevokeAll: () -> Unit
) {
    if (sessions.isEmpty()) {
        EmptyStateBox(
            icon = Icons.Default.Devices,
            title = "NINGÚN CLIENTE PC EMPAREJADO",
            subtitle = "Cuando Akatsuki Desktop valide el PIN de 6 dígitos en POST /api/v1/auth/pair, la sesión Bearer aparecerá aquí."
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListHeaderText(
                text = "SESIONES BEARER ACTIVAS (${sessions.size})",
                color = AkatsukiPalette.ZetsuBioGreen
            )
            CyberButton(
                text = "REVOCAR TODAS",
                onClick = onRevokeAll,
                variant = CyberButtonVariant.Danger,
                size = CyberButtonSize.Sm
            )
        }

        sessions.forEach { session ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberChamferSurface(
                        chamfer = 9.dp,
                        backgroundColor = AkatsukiPalette.ShadowMantle,
                        borderColor = AkatsukiPalette.ZetsuBioGreen.copy(alpha = 0.55f),
                        borderWidth = 1.5.dp
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AkatsukiPalette.ZetsuBioGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ListItemText(
                            text = session.clientName,
                            color = AkatsukiPalette.CloudWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    MonoTelemetry(
                        text = "Conectado: ${session.connectedAt}",
                        color = AkatsukiPalette.NeonTelemetry
                    )
                    MonoHash(
                        text = "Token: ${session.bearerToken.take(28)}...",
                        color = AkatsukiPalette.HiddenRainAsh,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                CyberButton(
                    text = "REVOCAR",
                    onClick = { onRevokeSession(session.bearerToken) },
                    variant = CyberButtonVariant.Danger,
                    size = CyberButtonSize.Sm
                )
            }
        }
    }
}

@Composable
private fun LocalStorageBrowserTabContent(
    currentPath: String,
    entries: List<FileEntry>,
    error: String?,
    onNavigateDir: (String) -> Unit,
    onNavigateParent: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Barra de ruta actual y botón para subir de nivel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .cyberChamferSurface(
                    chamfer = 9.dp,
                    backgroundColor = AkatsukiPalette.ShadowMantle,
                    borderColor = AkatsukiPalette.PanelSubtleBorder,
                    borderWidth = 1.5.dp
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ListHeaderText(text = "DIRECTORIO EXPUESTO VÍA /API/V1/FILES/LIST")
                MonoTelemetry(
                    text = currentPath,
                    color = AkatsukiPalette.NeonTelemetry
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            CyberButton(
                text = "SUBIR",
                onClick = onNavigateParent,
                variant = CyberButtonVariant.Secondary,
                size = CyberButtonSize.Sm,
                leadingIcon = Icons.Default.ArrowUpward
            )
        }

        if (error != null) {
            BodyText(
                text = error,
                color = AkatsukiPalette.PaperBombAmber
            )
        } else if (entries.isEmpty()) {
            EmptyStateBox(
                icon = Icons.Default.Folder,
                title = "DIRECTORIO VACÍO",
                subtitle = "No se encontraron archivos ni subcarpetas en esta ruta."
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                entries.take(50).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cyberChamferSurface(
                                chamfer = 9.dp,
                                backgroundColor = AkatsukiPalette.UchihaSlate.copy(alpha = 0.55f),
                                borderColor = AkatsukiPalette.PanelSubtleBorder,
                                borderWidth = 1.dp
                            )
                            .clickable(enabled = entry.isDir) {
                                if (entry.isDir) {
                                    onNavigateDir(entry.path)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (entry.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isDir) AkatsukiPalette.AkatsukiCrimson else AkatsukiPalette.NeonTelemetry,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                ListItemText(
                                    text = entry.name,
                                    color = if (entry.isDir) AkatsukiPalette.CloudWhite else AkatsukiPalette.ShinobiSilver
                                )
                                MonoHash(
                                    text = entry.modifiedAt,
                                    color = AkatsukiPalette.HiddenRainAsh,
                                    maxLines = 1
                                )
                            }
                        }

                        MonoTelemetry(
                            text = if (entry.isDir) "DIR" else ByteFormatUtils.formatBytes(entry.sizeBytes),
                            color = if (entry.isDir) AkatsukiPalette.CrimsonHighlight else AkatsukiPalette.NeonTelemetry
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityAuditLogsTabContent(
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

@Composable
private fun HudBottomStatusBar(state: HudUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AkatsukiPalette.ShadowMantle)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AkatsukiPalette.PanelSubtleBorder)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = AkatsukiPalette.CrimsonHighlight,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Heading3(
                    text = "ID: ${state.deviceId}",
                    color = AkatsukiPalette.CrimsonHighlight
                )
            }
            MonoTelemetry(
                text = "API v1 • 9 ENDPOINTS • SHA-256 STREAM",
                color = AkatsukiPalette.HiddenRainAsh
            )
        }
    }
}

@Composable
private fun TelemetryDataCard(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
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
private fun HudTabButton(
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
private fun EmptyStateBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun TofuQrCodeDialog(
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

@Composable
private fun NodeConfigurationDialog(
    currentName: String,
    currentPort: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var nameInput by remember(currentName) { mutableStateOf(currentName) }
    var portInput by remember(currentPort) { mutableStateOf(currentPort.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        CyberPanel(
            title = "CONFIGURACIÓN DEL NODO ANDROID",
            subtitle = "IDENTIDAD mDNS Y PUERTO HTTPS",
            headerIcon = Icons.Default.Terminal,
            accentColor = AkatsukiPalette.AkatsukiCrimson
        ) {
            ListHeaderText(text = "ALIAS DEL DISPOSITIVO (DEVICE NAME)")
            Spacer(modifier = Modifier.height(6.dp))
            CyberTextField(
                value = nameInput,
                onValueChange = { nameInput = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ListHeaderText(text = "PUERTO HTTPS (DEFECTO: 8443)")
            Spacer(modifier = Modifier.height(6.dp))
            CyberTextField(
                value = portInput,
                onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberButton(
                    text = "CANCELAR",
                    onClick = onDismiss,
                    variant = CyberButtonVariant.Ghost,
                    size = CyberButtonSize.Sm,
                    modifier = Modifier.weight(1f)
                )
                CyberButton(
                    text = "GUARDAR",
                    onClick = {
                        val parsedPort = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 8443
                        onSave(nameInput, parsedPort)
                    },
                    variant = CyberButtonVariant.Primary,
                    size = CyberButtonSize.Sm,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cyberChamferSurface(
                chamfer = 9.dp,
                backgroundColor = AkatsukiPalette.ShadowMantle,
                borderColor = AkatsukiPalette.RinneganCyberCyan.copy(alpha = 0.55f),
                borderWidth = 1.5.dp
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = AkatsukiTheme.typography.monoData.copy(color = AkatsukiPalette.CloudWhite),
            cursorBrush = SolidColor(AkatsukiPalette.AkatsukiCrimson),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copiado al portapapeles", Toast.LENGTH_SHORT).show()
}
