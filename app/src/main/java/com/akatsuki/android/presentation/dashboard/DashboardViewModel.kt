package com.akatsuki.android.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.akatsuki.android.di.AppContainer
import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.presentation.dashboard.HudIntent.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class DashboardViewModel(
    private val container: AppContainer,
    private val onStartForegroundServer: () -> Unit,
    private val onStopForegroundServer: () -> Unit
) : ViewModel() {

    private val _uiState = MutableStateFlow(HudUiState())
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()

    init {
        initializeIdentityAndCertificate()
        observeReactiveFlows()
        startPinCountdownAndNetworkTicker()
    }

    fun onIntent(intent: HudIntent) {
        when (intent) {
            is ToggleServer -> {
                if (_uiState.value.isServerRunning || _uiState.value.isServerStarting) {
                    onStopForegroundServer()
                } else {
                    refreshSystemStatus()
                    onStartForegroundServer()
                }
            }
            is RotatePin -> {
                val newPin = container.sessionAuthRepository.rotatePin()
                container.telemetryRepository.recordLog(
                    tag = "PIN-HUD",
                    message = "Nuevo PIN temporal generado: ${newPin.pinCode} (válido por 5m).",
                    severity = AuditSeverity.INFO
                )
                updatePinTimerMetrics()
            }
            is RegenerateTlsCertificate -> {
                viewModelScope.launch {
                    val bundle = container.certificateRepository.regenerateCertificateBundle()
                    _uiState.update {
                        it.copy(
                            tlsFingerprintSha256 = bundle.fingerprintSha256,
                            tlsAlgorithmLabel = bundle.algorithmDescription
                        )
                    }
                    container.telemetryRepository.recordLog(
                        tag = "TLS-KEYGEN",
                        message = "Certificado X.509 regenerado. Nueva huella SHA-256: ${bundle.fingerprintSha256.take(17)}...",
                        severity = AuditSeverity.WARNING
                    )
                    container.ktorServer.restartIfRunning()
                }
            }
            is RefreshNetworkAndPermissions -> {
                refreshSystemStatus()
                loadDirectoryForPreview(_uiState.value.currentBrowserPath)
            }
            is SelectTab -> {
                _uiState.update { it.copy(selectedTab = intent.tab) }
                if (intent.tab == HudTelemetryTab.STORAGE_BROWSER) {
                    loadDirectoryForPreview(_uiState.value.currentBrowserPath)
                }
            }
            is SetShowQrModal -> {
                _uiState.update { it.copy(showQrCodeModal = intent.visible) }
            }
            is SetShowConfigModal -> {
                _uiState.update { it.copy(showNodeConfigModal = intent.visible) }
            }
            is SaveNodeConfig -> {
                viewModelScope.launch {
                    val previousName = container.nodeConfigRepository.deviceNameFlow.value
                    val previousPort = container.nodeConfigRepository.portFlow.value
                    container.nodeConfigRepository.updateDeviceName(intent.newDeviceName)
                    container.nodeConfigRepository.updatePort(intent.newPort)
                    val updatedName = container.nodeConfigRepository.deviceNameFlow.value
                    val updatedPort = container.nodeConfigRepository.portFlow.value
                    _uiState.update { it.copy(showNodeConfigModal = false) }
                    container.telemetryRepository.recordLog(
                        tag = "NODE-CFG",
                        message = "Configuración actualizada: '$updatedName' en puerto $updatedPort",
                        severity = AuditSeverity.INFO
                    )
                    if (previousPort != updatedPort || previousName != updatedName) {
                        container.ktorServer.restartIfRunning()
                    }
                }
            }
            is RevokeSession -> {
                container.managePinAndPairingUseCase.revokeSession(intent.bearerToken)
            }
            is RevokeAllSessions -> {
                container.sessionAuthRepository.revokeAllSessions()
                container.telemetryRepository.recordLog(
                    tag = "AUTH-REVOKE",
                    message = "Todas las sesiones activas de clientes PC fueron revocadas.",
                    severity = AuditSeverity.WARNING
                )
            }
            is NavigateDirectory -> {
                loadDirectoryForPreview(intent.path)
            }
            is NavigateParentDirectory -> {
                val current = _uiState.value.currentBrowserPath
                val allowedRoot = container.fileSystemGateway.getAllowedRootPaths().firstOrNull()
                    ?: "/storage/emulated/0"
                val parent = File(current).parentFile?.path ?: allowedRoot
                val target = if (parent.startsWith(allowedRoot)) parent else allowedRoot
                loadDirectoryForPreview(target)
            }
            is ClearAuditLogs -> {
                container.telemetryRepository.clearLogs()
            }
        }
    }

    private fun initializeIdentityAndCertificate() {
        refreshSystemStatus()
        viewModelScope.launch {
            runCatching {
                container.certificateRepository.getOrCreateCertificateBundle()
            }.onSuccess { bundle ->
                _uiState.update {
                    it.copy(
                        tlsFingerprintSha256 = bundle.fingerprintSha256,
                        tlsAlgorithmLabel = bundle.algorithmDescription
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(tlsFingerprintSha256 = "ERROR TLS: ${err.message}")
                }
            }
        }
    }

    private fun observeReactiveFlows() {
        viewModelScope.launch {
            combine(
                container.telemetryRepository.serverState,
                container.sessionAuthRepository.pinState,
                container.sessionAuthRepository.activeSessions,
                container.telemetryRepository.transfers,
                container.telemetryRepository.auditLogs
            ) { serverState, pinState, sessions, transfers, logs ->
                val now = System.currentTimeMillis()
                val remainingPinSec = pinState.remainingSeconds(now)
                val lockedSec = if (pinState.isLocked(now)) {
                    ((pinState.lockedUntilMillis - now + 999L) / 1000L).coerceAtLeast(0L)
                } else {
                    0L
                }
                _uiState.value.copy(
                    serverState = serverState,
                    pinCode = pinState.pinCode,
                    pinRemainingSeconds = remainingPinSec,
                    pinFailedAttempts = pinState.failedAttempts,
                    pinLockedRemainingSeconds = lockedSec,
                    activeSessions = sessions,
                    transfers = transfers,
                    auditLogs = logs
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }

        viewModelScope.launch {
            combine(
                container.nodeConfigRepository.deviceIdFlow,
                container.nodeConfigRepository.deviceNameFlow,
                container.nodeConfigRepository.portFlow
            ) { id, name, port ->
                Triple(id, name, port)
            }.collect { (id, name, port) ->
                _uiState.update {
                    it.copy(deviceId = id, deviceName = name, port = port)
                }
            }
        }
    }

    private fun startPinCountdownAndNetworkTicker() {
        viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                delay(1_000L)
                tickCount++
                updatePinTimerMetrics()
                if (tickCount % 5 == 0) {
                    refreshSystemStatus()
                }
            }
        }
    }

    private fun updatePinTimerMetrics() {
        val now = System.currentTimeMillis()
        val currentPin = container.sessionAuthRepository.pinState.value
        if (currentPin.isExpired(now)) {
            val rotated = container.sessionAuthRepository.rotatePin(now)
            container.telemetryRepository.recordLog(
                tag = "PIN-AUTO",
                message = "PIN temporal rotado automáticamente por expiración de 5m (${rotated.pinCode}).",
                severity = AuditSeverity.INFO
            )
        } else {
            val remainingPinSec = currentPin.remainingSeconds(now)
            val lockedSec = if (currentPin.isLocked(now)) {
                ((currentPin.lockedUntilMillis - now + 999L) / 1000L).coerceAtLeast(0L)
            } else {
                0L
            }
            _uiState.update {
                it.copy(
                    pinCode = currentPin.pinCode,
                    pinRemainingSeconds = remainingPinSec,
                    pinFailedAttempts = currentPin.failedAttempts,
                    pinLockedRemainingSeconds = lockedSec
                )
            }
        }
    }

    private fun refreshSystemStatus() {
        val ip = container.networkDiscoveryGateway.resolveLocalLanIpAddress()
        val hasPerm = container.fileSystemGateway.hasAllFilesAccessPermission()
        val storage = container.fileSystemGateway.queryStorageMetrics()
        _uiState.update {
            it.copy(
                localIpAddress = ip,
                hasAllFilesPermission = hasPerm,
                storageMetrics = storage
            )
        }
    }

    private fun loadDirectoryForPreview(path: String) {
        viewModelScope.launch {
            runCatching {
                container.fileSystemGateway.listDirectory(path)
            }.onSuccess { listing ->
                _uiState.update {
                    it.copy(
                        currentBrowserPath = listing.currentPath,
                        browserEntries = listing.entries,
                        browserError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(browserError = err.message)
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            container: AppContainer,
            onStartForegroundServer: () -> Unit,
            onStopForegroundServer: () -> Unit
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(
                    container = container,
                    onStartForegroundServer = onStartForegroundServer,
                    onStopForegroundServer = onStopForegroundServer
                ) as T
            }
        }
    }
}
