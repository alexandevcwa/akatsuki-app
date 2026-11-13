package com.akatsuki.android.domain.usecase

import com.akatsuki.android.domain.model.ActiveSession
import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.model.InvalidRequestParameterException
import com.akatsuki.android.domain.repository.FileSystemGateway
import com.akatsuki.android.domain.repository.NetworkDiscoveryGateway
import com.akatsuki.android.domain.repository.NodeConfigRepository
import com.akatsuki.android.domain.repository.SessionAuthRepository
import com.akatsuki.android.domain.repository.TelemetryRepository
import com.akatsuki.android.domain.repository.TlsCertificateRepository
import android.os.Build

/**
 * Orquesta el emparejamiento seguro TOFU (Sección 3.2: POST /api/v1/auth/pair)
 * y la revocación de sesión (Sección 3.7: POST /api/v1/auth/revoke).
 */
class ManagePinAndPairingUseCase(
    private val sessionAuthRepository: SessionAuthRepository,
    private val certificateRepository: TlsCertificateRepository,
    private val fileSystemGateway: FileSystemGateway,
    private val networkDiscoveryGateway: NetworkDiscoveryGateway,
    private val nodeConfigRepository: NodeConfigRepository,
    private val telemetryRepository: TelemetryRepository
) {
    suspend fun pairDevice(clientName: String, pinCode: String): ActiveSession {
        val normalizedClient = clientName.trim().ifEmpty { "Akatsuki Desktop Client" }
        val normalizedPin = pinCode.trim()
        if (normalizedPin.length != 6 || !normalizedPin.all { it.isDigit() }) {
            telemetryRepository.recordLog(
                tag = "AUTH",
                message = "Intento de emparejamiento con formato de PIN inválido desde '$normalizedClient'",
                severity = AuditSeverity.WARNING
            )
            throw InvalidRequestParameterException("El código PIN debe contener exactamente 6 dígitos numéricos.")
        }

        val certBundle = certificateRepository.getOrCreateCertificateBundle()
        val storage = fileSystemGateway.queryStorageMetrics()
        val ipAddress = networkDiscoveryGateway.resolveLocalLanIpAddress()

        return try {
            val session = sessionAuthRepository.verifyPinAndIssueSession(
                pinCode = normalizedPin,
                clientName = normalizedClient,
                deviceId = nodeConfigRepository.deviceIdFlow.value,
                deviceName = nodeConfigRepository.deviceNameFlow.value,
                model = "Android ${Build.VERSION.RELEASE}",
                ipAddress = ipAddress,
                port = nodeConfigRepository.portFlow.value,
                tlsCertFingerprintSha256 = certBundle.fingerprintSha256,
                storageMetrics = storage
            )
            telemetryRepository.recordLog(
                tag = "TOFU-PAIR",
                message = "Sesión establecida con '$normalizedClient' ($ipAddress:${session.device.port})",
                severity = AuditSeverity.SUCCESS
            )
            session
        } catch (e: Exception) {
            telemetryRepository.recordLog(
                tag = "AUTH-FAIL",
                message = "Fallo de autenticación desde '$normalizedClient': ${e.message}",
                severity = AuditSeverity.CRITICAL
            )
            throw e
        }
    }

    fun revokeSession(bearerToken: String): Boolean {
        val revoked = sessionAuthRepository.revokeBearerToken(bearerToken)
        if (revoked) {
            telemetryRepository.recordLog(
                tag = "AUTH-REVOKE",
                message = "Token de sesión revocado exitosamente.",
                severity = AuditSeverity.WARNING
            )
        }
        return revoked
    }
}
