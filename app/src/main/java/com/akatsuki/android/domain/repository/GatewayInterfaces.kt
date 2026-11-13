package com.akatsuki.android.domain.repository

import com.akatsuki.android.domain.model.ActiveSession
import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.model.DirectoryListing
import com.akatsuki.android.domain.model.DownloadStreamDescriptor
import com.akatsuki.android.domain.model.EphemeralPinState
import com.akatsuki.android.domain.model.SecurityAuditLog
import com.akatsuki.android.domain.model.ServerLifecycleState
import com.akatsuki.android.domain.model.StorageMetrics
import com.akatsuki.android.domain.model.TlsCertificateBundle
import com.akatsuki.android.domain.model.TransferTelemetryItem
import com.akatsuki.android.domain.model.VerifiedUploadResult
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Gestión del Certificado X.509 Autofirmado y huella SHA-256 (Capa 1 y Capa 2)
 */
interface TlsCertificateRepository {
    suspend fun getOrCreateCertificateBundle(): TlsCertificateBundle
    suspend fun regenerateCertificateBundle(): TlsCertificateBundle
}

/**
 * Autenticación por PIN efímero de 6 dígitos + Sesiones Bearer Token (Capa 3)
 */
interface SessionAuthRepository {
    val pinState: StateFlow<EphemeralPinState>
    val activeSessions: StateFlow<List<ActiveSession>>

    fun rotatePin(nowMillis: Long = System.currentTimeMillis()): EphemeralPinState
    fun verifyPinAndIssueSession(
        pinCode: String,
        clientName: String,
        deviceId: String,
        deviceName: String,
        model: String,
        ipAddress: String,
        port: Int,
        tlsCertFingerprintSha256: String,
        storageMetrics: StorageMetrics,
        nowMillis: Long = System.currentTimeMillis()
    ): ActiveSession

    fun validateBearerToken(
        bearerToken: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): ActiveSession
    fun revokeBearerToken(bearerToken: String): Boolean
    fun revokeAllSessions()
}

/**
 * Operaciones del sistema de archivos de Android con verificación SHA-256 en streaming (Capa 4)
 */
interface FileSystemGateway {
    fun getAllowedRootPaths(): List<String>
    fun hasAllFilesAccessPermission(): Boolean
    fun queryStorageMetrics(): StorageMetrics
    suspend fun listDirectory(requestedPath: String): DirectoryListing
    suspend fun createDirectory(requestedPath: String): String
    suspend fun deletePath(requestedPath: String): String
    suspend fun prepareDownload(requestedPath: String): Pair<DownloadStreamDescriptor, File>
    suspend fun streamDownloadRange(
        file: File,
        startOffset: Long,
        length: Long,
        outputStream: OutputStream,
        onProgress: (bytesTransferred: Long, speedMbps: Double) -> Unit
    )
    suspend fun receiveVerifiedUploadStream(
        destinationPath: String,
        expectedSha256: String?,
        totalBytesHint: Long,
        inputStream: InputStream,
        onProgress: (bytesTransferred: Long, speedMbps: Double) -> Unit
    ): VerifiedUploadResult
}

/**
 * Descubrimiento de red local y publicación mDNS/NSD (_akatsuki._tcp.)
 */
interface NetworkDiscoveryGateway {
    fun resolveLocalLanIpAddress(): String
    fun publishMdnsService(
        serviceName: String,
        port: Int,
        fingerprintSha256: String,
        deviceId: String,
        onStatusChanged: (Boolean) -> Unit
    )
    fun unpublishMdnsService()
}

/**
 * Configuración persistente del nodo Android (DataStore)
 */
interface NodeConfigRepository {
    val deviceIdFlow: StateFlow<String>
    val deviceNameFlow: StateFlow<String>
    val portFlow: StateFlow<Int>

    suspend fun updateDeviceName(newName: String)
    suspend fun updatePort(newPort: Int)
}

/**
 * Telemetría en tiempo real y auditoría para el HUD Cyberpunk
 */
interface TelemetryRepository {
    val serverState: StateFlow<ServerLifecycleState>
    val transfers: StateFlow<List<TransferTelemetryItem>>
    val auditLogs: StateFlow<List<SecurityAuditLog>>

    fun updateServerState(state: ServerLifecycleState)
    fun upsertTransfer(item: TransferTelemetryItem)
    fun recordLog(tag: String, message: String, severity: AuditSeverity = AuditSeverity.INFO)
    fun clearLogs()
}
