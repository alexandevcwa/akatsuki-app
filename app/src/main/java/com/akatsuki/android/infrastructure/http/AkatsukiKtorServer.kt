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
import io.ktor.server.engine. embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

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
    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    suspend fun startServer(): ServerLifecycleState = mutex.withLock {
        val currentState = telemetryRepository.serverState.value
        if (currentState is ServerLifecycleState.Running && engine != null) {
            return currentState
        }

        telemetryRepository.updateServerState(ServerLifecycleState.Starting)

        return withContext(Dispatchers.IO) {
            runCatching {
                val certBundle = certificateRepository.getOrCreateCertificateBundle()
                val port = nodeConfigRepository.portFlow.value
                val ipAddress = networkDiscoveryGateway.resolveLocalLanIpAddress()
                val startedAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

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
                            keyStorePassword = { certBundle.keyStorePassword },
                            privateKeyPassword = { certBundle.keyStorePassword }
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

                val initialRunningState = ServerLifecycleState.Running(
                    ipAddress = ipAddress,
                    port = port,
                    tlsCertFingerprintSha256 = certBundle.fingerprintSha256,
                    nsdPublished = false,
                    startedAtIso = startedAtIso
                )
                telemetryRepository.updateServerState(initialRunningState)

                telemetryRepository.recordLog(
                    tag = "HTTPS-TLS",
                    message = "Servidor Ktor HTTPS activo en https://$ipAddress:$port (${certBundle.algorithmDescription})",
                    severity = AuditSeverity.SUCCESS
                )

                networkDiscoveryGateway.publishMdnsService(
                    serviceName = "AkatsukiNode",
                    port = port,
                    fingerprintSha256 = certBundle.fingerprintSha256,
                    deviceId = nodeConfigRepository.deviceIdFlow.value
                ) { isPublished ->
                    val latest = telemetryRepository.serverState.value
                    if (latest is ServerLifecycleState.Running) {
                        telemetryRepository.updateServerState(latest.copy(nsdPublished = isPublished))
                    }
                }

                telemetryRepository.serverState.value
            }.getOrElse { error ->
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

    suspend fun stopServer() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                networkDiscoveryGateway.unpublishMdnsService()
                engine?.stop(gracePeriodMillis = 400, timeoutMillis = 1200)
            }
            engine = null
            sessionAuthRepository.revokeAllSessions()
            telemetryRepository.updateServerState(ServerLifecycleState.Stopped)
            telemetryRepository.recordLog(
                tag = "HTTPS-STOP",
                message = "Servidor HTTPS detenido y sesiones revocadas.",
                severity = AuditSeverity.WARNING
            )
        }
    }

    fun isRunning(): Boolean = engine != null && telemetryRepository.serverState.value is ServerLifecycleState.Running
}
