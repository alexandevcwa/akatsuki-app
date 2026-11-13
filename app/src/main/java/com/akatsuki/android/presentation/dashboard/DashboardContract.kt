package com.akatsuki.android.presentation.dashboard

import com.akatsuki.android.domain.model.ActiveSession
import com.akatsuki.android.domain.model.FileEntry
import com.akatsuki.android.domain.model.SecurityAuditLog
import com.akatsuki.android.domain.model.ServerLifecycleState
import com.akatsuki.android.domain.model.StorageMetrics
import com.akatsuki.android.domain.model.TransferTelemetryItem
import java.util.Locale

enum class HudTelemetryTab {
    TRANSFERS,
    SESSIONS,
    STORAGE_BROWSER,
    AUDIT_LOGS
}

data class HudUiState(
    val serverState: ServerLifecycleState = ServerLifecycleState.Stopped,
    val deviceId: String = "ak-node-android-01",
    val deviceName: String = "ANDROID NODE // ITACHI",
    val localIpAddress: String = "127.0.0.1",
    val port: Int = 8443,
    val tlsFingerprintSha256: String = "GENERANDO HUELLA SHA-256...",
    val tlsAlgorithmLabel: String = "ECDSA P-256 (TLS 1.3)",
    val pinCode: String = "------",
    val pinRemainingSeconds: Long = 300L,
    val pinFailedAttempts: Int = 0,
    val pinLockedRemainingSeconds: Long = 0L,
    val showQrCodeModal: Boolean = false,
    val showNodeConfigModal: Boolean = false,
    val hasAllFilesPermission: Boolean = true,
    val storageMetrics: StorageMetrics = StorageMetrics(274_877_906_944L, 158_913_789_952L),
    val activeSessions: List<ActiveSession> = emptyList(),
    val transfers: List<TransferTelemetryItem> = emptyList(),
    val auditLogs: List<SecurityAuditLog> = emptyList(),
    val selectedTab: HudTelemetryTab = HudTelemetryTab.TRANSFERS,
    val currentBrowserPath: String = "/storage/emulated/0",
    val browserEntries: List<FileEntry> = emptyList(),
    val browserError: String? = null
) {
    val httpsEndpoint: String
        get() = "https://$localIpAddress:$port"

    val isServerRunning: Boolean
        get() = serverState is ServerLifecycleState.Running

    val isServerStarting: Boolean
        get() = serverState is ServerLifecycleState.Starting

    val isMdnsPublished: Boolean
        get() = (serverState as? ServerLifecycleState.Running)?.nsdPublished == true

    val qrCodePayloadJson: String
        get() = """{"deviceId":"$deviceId","deviceName":"$deviceName","ipAddress":"$localIpAddress","port":$port,"pinCode":"$pinCode","tlsCertFingerprintSha256":"$tlsFingerprintSha256","protocol":"https-tofu"}"""
}

sealed interface HudIntent {
    data object ToggleServer : HudIntent
    data object RotatePin : HudIntent
    data object RegenerateTlsCertificate : HudIntent
    data object RefreshNetworkAndPermissions : HudIntent
    data class SelectTab(val tab: HudTelemetryTab) : HudIntent
    data class SetShowQrModal(val visible: Boolean) : HudIntent
    data class SetShowConfigModal(val visible: Boolean) : HudIntent
    data class SaveNodeConfig(val newDeviceName: String, val newPort: Int) : HudIntent
    data class RevokeSession(val bearerToken: String) : HudIntent
    data object RevokeAllSessions : HudIntent
    data class NavigateDirectory(val path: String) : HudIntent
    data object NavigateParentDirectory : HudIntent
    data object ClearAuditLogs : HudIntent
}

object ByteFormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatDurationMmSs(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val mins = clamped / 60L
        val secs = clamped % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
