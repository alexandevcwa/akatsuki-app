package com.akatsuki.android.infrastructure.http

import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.model.ServerLifecycleState
import com.akatsuki.android.domain.repository.NetworkDiscoveryGateway
import com.akatsuki.android.domain.repository.NodeConfigRepository
import com.akatsuki.android.domain.repository.SessionAuthRepository
import com.akatsuki.android.domain.repository.TelemetryRepository
import com.akatsuki.android.domain.repository.TlsCertificateRepository
import com.akatsuki.android.domain.usecase.FileOperationsUseCases
import com.akatsuki.android.domain.usecase.ManagePinAndPairingUseCase
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * Gestor del ciclo de vida del Servidor Web HTTPS embebido (Ktor + Netty + TLS 1.3).
 * Opera exclusivamente sobre HTTPS en el puerto configurado (8443 por defecto).
 */
class AkatsukiKtorServer(
    private val nodeConfigRepository: NodeConfigRepository,
    private val certificateRepository: TlsCertificateRepository,
    private val sessionAuthRepository: SessionAuthRepository,
    private val networkDiscoveryGateway: NetworkDiscoveryGateway,
    private val telemetryRepository: TelemetryRepository,
    private val managePinAndPairingUseCase: ManagePinAndPairingUseCase,
    private val fileOperationsUseCases: FileOperationsUseCases
) {
    private val mutex = Mutex()
    private val refreshLock = Any()
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    @Volatile
    private var activeSessionToken: String? = null
    private var networkObserverHandle: AutoCloseable? = null
    private var networkRefreshJob: Job? = null

    suspend fun startServer(): ServerLifecycleState = mutex.withLock {
        startServerLocked()
    }

    suspend fun stopServer() = mutex.withLock {
        stopServerLocked()
    }

    suspend fun restartIfRunning(): ServerLifecycleState = mutex.withLock {
        val currentlyRunning = engine != null && telemetryRepository.serverState.value is ServerLifecycleState.Running
        if (!currentlyRunning) {
            return@withLock telemetryRepository.serverState.value
        }
        stopServerLocked()
        startServerLocked()
    }

    fun isRunning(): Boolean = engine != null && telemetryRepository.serverState.value is ServerLifecycleState.Running

    private suspend fun startServerLocked(): ServerLifecycleState {
        val currentState = telemetryRepository.serverState.value
        if (currentState is ServerLifecycleState.Running && engine != null) {
            return currentState
        }

        telemetryRepository.updateServerState(ServerLifecycleState.Starting)

        return withContext(Dispatchers.IO) {
            runCatching {
                nodeConfigRepository.awaitInitialized()

                val certBundle = certificateRepository.getOrCreateCertificateBundle()
                val port = nodeConfigRepository.portFlow.value
                val deviceId = nodeConfigRepository.deviceIdFlow.value
                val deviceName = nodeConfigRepository.deviceNameFlow.value
                val ipAddress = networkDiscoveryGateway.resolveLocalLanIpAddress()
                val startedAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                val sessionToken = UUID.randomUUID().toString()

                // Asegurar un PIN fresco al levantar el servidor
                sessionAuthRepository.rotatePin()

                val env = applicationEnvironment()
                val server = embeddedServer(
                    factory = Netty,
                    environment = env,
                    configure = {
                        sslConnector(
                            keyStore = certBundle.keyStore,
                            keyAlias = certBundle.keyAlias,
                            keyStorePassword = { certBundle.keyStorePassword.copyOf() },
                            privateKeyPassword = { certBundle.keyStorePassword.copyOf() }
                        ) {
                            this.host = "0.0.0.0"
                            this.port = port
                        }
                    },
                    module = {
                        configureAkatsukiApi(
                            nodeConfigRepository = nodeConfigRepository,
                            certificateRepository = certificateRepository,
                            sessionAuthRepository = sessionAuthRepository,
                            managePinAndPairingUseCase = managePinAndPairingUseCase,
                            fileOperationsUseCases = fileOperationsUseCases
                        )
                    }
                )

                server.start(wait = false)
                engine = server
                activeSessionToken = sessionToken

                val initialRunningState = ServerLifecycleState.Running(
                    ipAddress = ipAddress,
                    port = port,
                    tlsCertFingerprintSha256 = certBundle.fingerprintSha256,
                    nsdPublished = false,
                    startedAtIso = startedAtIso,
                    nsdServiceName = null
                )
                telemetryRepository.updateServerState(initialRunningState)

                telemetryRepository.recordLog(
                    tag = "HTTPS-TLS",
                    message = "Servidor Ktor HTTPS activo en https://$ipAddress:$port (${certBundle.algorithmDescription})",
                    severity = AuditSeverity.SUCCESS
                )

                publishMdns(
                    sessionToken = sessionToken,
                    port = port,
                    fingerprintSha256 = certBundle.fingerprintSha256,
                    deviceId = deviceId,
                    deviceName = deviceName
                )

                runCatching { networkObserverHandle?.close() }
                networkObserverHandle = networkDiscoveryGateway.observeNetworkChanges {
                    scheduleNetworkRefresh(sessionToken)
                }

                telemetryRepository.serverState.value
            }.getOrElse { error ->
                activeSessionToken = null
                synchronized(refreshLock) {
                    networkRefreshJob?.cancel()
                    networkRefreshJob = null
                }
                runCatching { networkObserverHandle?.close() }
                networkObserverHandle = null
                runCatching { networkDiscoveryGateway.unpublishMdnsService() }
                runCatching { engine?.stop(gracePeriodMillis = 200, timeoutMillis = 500) }
                engine = null

                val msg = error.message ?: "No se pudo iniciar el servidor HTTPS en el puerto ${nodeConfigRepository.portFlow.value}"
                telemetryRepository.recordLog(
                    tag = "SERVER-ERR",
                    message = msg,
                    severity = AuditSeverity.CRITICAL
                )
                val errorState = ServerLifecycleState.Error(msg)
                telemetryRepository.updateServerState(errorState)
                errorState
            }
        }
    }

    private suspend fun stopServerLocked() {
        val wasActive = engine != null || telemetryRepository.serverState.value !is ServerLifecycleState.Stopped
        activeSessionToken = null
        synchronized(refreshLock) {
            networkRefreshJob?.cancel()
            networkRefreshJob = null
        }
        runCatching { networkObserverHandle?.close() }
        networkObserverHandle = null

        withContext(Dispatchers.IO) {
            runCatching {
                networkDiscoveryGateway.unpublishMdnsService()
                engine?.stop(gracePeriodMillis = 400, timeoutMillis = 1200)
            }
        }
        engine = null

        if (wasActive) {
            sessionAuthRepository.revokeAllSessions()
            telemetryRepository.updateServerState(ServerLifecycleState.Stopped)
            telemetryRepository.recordLog(
                tag = "HTTPS-STOP",
                message = "Servidor HTTPS detenido y sesiones revocadas.",
                severity = AuditSeverity.WARNING
            )
        }
    }

    private fun publishMdns(
        sessionToken: String,
        port: Int,
        fingerprintSha256: String,
        deviceId: String,
        deviceName: String
    ) {
        networkDiscoveryGateway.publishMdnsService(
            serviceName = "AkatsukiNode",
            port = port,
            fingerprintSha256 = fingerprintSha256,
            deviceId = deviceId,
            deviceName = deviceName
        ) { isPublished, resolvedServiceName ->
            if (activeSessionToken != sessionToken) return@publishMdnsService
            telemetryRepository.updateServerState { latest ->
                if (latest is ServerLifecycleState.Running && activeSessionToken == sessionToken) {
                    latest.copy(
                        nsdPublished = isPublished,
                        nsdServiceName = resolvedServiceName ?: if (isPublished) latest.nsdServiceName else null
                    )
                } else {
                    latest
                }
            }
        }
    }

    private fun scheduleNetworkRefresh(sessionToken: String) {
        if (activeSessionToken != sessionToken) return
        synchronized(refreshLock) {
            networkRefreshJob?.cancel()
            networkRefreshJob = serverScope.launch {
                delay(450L.milliseconds)
                refreshNetworkAndMdns(sessionToken)
            }
        }
    }

    private suspend fun refreshNetworkAndMdns(sessionToken: String) = mutex.withLock {
        if (activeSessionToken != sessionToken || engine == null) return@withLock
        val current = telemetryRepository.serverState.value as? ServerLifecycleState.Running ?: return@withLock
        val newIp = withContext(Dispatchers.IO) {
            networkDiscoveryGateway.resolveLocalLanIpAddress()
        }
        val ipChanged = newIp != current.ipAddress
        if (ipChanged) {
            telemetryRepository.updateServerState { latest ->
                if (latest is ServerLifecycleState.Running && activeSessionToken == sessionToken) {
                    latest.copy(ipAddress = newIp)
                } else {
                    latest
                }
            }
            telemetryRepository.recordLog(
                tag = "NET-LAN",
                message = "Interfaz LAN actualizada: https://$newIp:${current.port}",
                severity = AuditSeverity.INFO
            )
        }

        val hasLanIp = newIp != "127.0.0.1"
        if (hasLanIp && (!current.nsdPublished || ipChanged)) {
            withContext(Dispatchers.IO) {
                publishMdns(
                    sessionToken = sessionToken,
                    port = current.port,
                    fingerprintSha256 = current.tlsCertFingerprintSha256,
                    deviceId = nodeConfigRepository.deviceIdFlow.value,
                    deviceName = nodeConfigRepository.deviceNameFlow.value
                )
            }
        }
    }
}
