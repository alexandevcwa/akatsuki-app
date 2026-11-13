package com.akatsuki.android.domain.model

import java.security.KeyStore

/**
 * Estado del ciclo de vida del Servidor Web HTTPS (State Pattern)
 */
sealed interface ServerLifecycleState {
    data object Stopped : ServerLifecycleState
    data object Starting : ServerLifecycleState
    data class Running(
        val ipAddress: String,
        val port: Int,
        val tlsCertFingerprintSha256: String,
        val nsdPublished: Boolean,
        val startedAtIso: String
    ) : ServerLifecycleState
    data class Error(val message: String) : ServerLifecycleState
}

/**
 * Identidad criptográfica y de red del nodo Android
 */
data class DeviceIdentity(
    val deviceId: String,
    val deviceName: String,
    val model: String,
    val ipAddress: String,
    val port: Int,
    val tlsCertFingerprintSha256: String,
    val discoveredVia: String = "https-tofu",
    val paired: Boolean = true
)

/**
 * Sesión activa autenticada mediante PIN efímero (Sección 3.2)
 */
data class ActiveSession(
    val device: DeviceIdentity,
    val bearerToken: String,
    val clientName: String,
    val connectedAt: String,
    val androidStorageTotalBytes: Long,
    val androidStorageFreeBytes: Long
)

/**
 * Estado del PIN efímero de 6 dígitos y protección contra fuerza bruta
 */
data class EphemeralPinState(
    val pinCode: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val failedAttempts: Int = 0,
    val lockedUntilMillis: Long = 0L
) {
    fun isLocked(nowMillis: Long): Boolean = nowMillis < lockedUntilMillis
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
    fun remainingSeconds(nowMillis: Long): Long =
        ((expiresAtMillis - nowMillis) / 1000L).coerceAtLeast(0L)
}

/**
 * Entrada individual de archivo o carpeta en Android (Sección 3.3)
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val modifiedAt: String,
    val extension: String?
)

/**
 * Listado de un directorio en Android (Sección 3.3)
 */
data class DirectoryListing(
    val currentPath: String,
    val hostname: String? = null,
    val entries: List<FileEntry>
)

/**
 * Métricas de espacio del dispositivo Android (Sección 3.7)
 */
data class StorageMetrics(
    val androidStorageTotalBytes: Long,
    val androidStorageFreeBytes: Long
)

/**
 * Resultado verificado de subida de archivo (Sección 3.5)
 */
data class VerifiedUploadResult(
    val verified: Boolean,
    val savedPath: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Descriptor para streaming de descarga de archivo (Sección 3.4)
 */
data class DownloadStreamDescriptor(
    val fileName: String,
    val canonicalPath: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Dirección de transferencia para telemetría HUD
 */
enum class TransferDirection {
    UPLOAD_PC_TO_ANDROID,
    DOWNLOAD_ANDROID_TO_PC
}

enum class TransferStatus {
    IN_PROGRESS,
    COMPLETED_VERIFIED,
    FAILED_INTEGRITY,
    FAILED_IO
}

/**
 * Telemetría en tiempo real de una transferencia de archivo
 */
data class TransferTelemetryItem(
    val id: String,
    val fileName: String,
    val remotePath: String,
    val direction: TransferDirection,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedMbps: Double,
    val expectedSha256: String?,
    val computedSha256: String?,
    val status: TransferStatus,
    val timestampFormatted: String
)

/**
 * Evento de bitácora táctica de seguridad y red
 */
data class SecurityAuditLog(
    val id: String,
    val timestamp: String,
    val tag: String,
    val message: String,
    val severity: AuditSeverity
)

enum class AuditSeverity {
    INFO,
    SUCCESS,
    WARNING,
    CRITICAL
}

/**
 * Contenedor del certificado X.509 y KeyStore TLS
 */
data class TlsCertificateBundle(
    val keyStore: KeyStore,
    val keyAlias: String,
    val keyStorePassword: CharArray,
    val fingerprintSha256: String,
    val algorithmDescription: String
)
