package com.akatsuki.android.infrastructure.http.dto

import com.akatsuki.android.domain.model.ActiveSession
import com.akatsuki.android.domain.model.DeviceIdentity
import com.akatsuki.android.domain.model.DirectoryListing
import com.akatsuki.android.domain.model.FileEntry
import com.akatsuki.android.domain.model.StorageMetrics
import com.akatsuki.android.domain.model.VerifiedUploadResult
import kotlinx.serialization.Serializable

/**
 * Modelos JSON con propiedades estrictamente en camelCase
 */

// GET /api/v1/health
@Serializable
data class HealthResponseDto(
    val status: String = "online",
    val deviceId: String,
    val deviceName: String,
    val model: String,
    val port: Int,
    val tlsCertFingerprintSha256: String
)

// POST /api/v1/auth/pair
@Serializable
data class PairRequestDto(
    val clientName: String = "Akatsuki Client",
    val pinCode: String
)

@Serializable
data class DeviceDto(
    val deviceId: String,
    val deviceName: String,
    val model: String,
    val ipAddress: String,
    val port: Int,
    val tlsCertFingerprintSha256: String,
    val discoveredVia: String = "https-tofu",
    val paired: Boolean = true
)

@Serializable
data class ActiveSessionResponseDto(
    val device: DeviceDto,
    val bearerToken: String,
    val connectedAt: String,
    val androidStorageTotalBytes: Long,
    val androidStorageFreeBytes: Long
)

// GET /api/v1/files/list
@Serializable
data class FileEntryDto(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val modifiedAt: String,
    val extension: String? = null
)

@Serializable
data class DirectoryListingResponseDto(
    val currentPath: String,
    val hostname: String? = null,
    val entries: List<FileEntryDto>
)

// POST /api/v1/files/upload
@Serializable
data class UploadVerifiedResponseDto(
    val verified: Boolean,
    val savedPath: String,
    val sizeBytes: Long,
    val sha256: String
)

// POST /api/v1/files/mkdir & DELETE /api/v1/files/delete
@Serializable
data class PathActionRequestDto(
    val path: String
)

@Serializable
data class MkdirResponseDto(
    val created: Boolean,
    val path: String
)

@Serializable
data class DeleteResponseDto(
    val deleted: Boolean,
    val path: String
)

// GET /api/v1/device/storage & POST /api/v1/auth/revoke
@Serializable
data class StorageResponseDto(
    val androidStorageTotalBytes: Long,
    val androidStorageFreeBytes: Long
)

@Serializable
data class RevokeResponseDto(
    val revoked: Boolean
)

@Serializable
data class ApiErrorResponseDto(
    val error: String,
    val message: String,
    val statusCode: Int,
    val retryAfterSeconds: Long? = null
)

// Mappers (Adapter Pattern)
fun DeviceIdentity.toDto(): DeviceDto = DeviceDto(
    deviceId = deviceId,
    deviceName = deviceName,
    model = model,
    ipAddress = ipAddress,
    port = port,
    tlsCertFingerprintSha256 = tlsCertFingerprintSha256,
    discoveredVia = discoveredVia,
    paired = paired
)

fun ActiveSession.toDto(): ActiveSessionResponseDto = ActiveSessionResponseDto(
    device = device.toDto(),
    bearerToken = bearerToken,
    connectedAt = connectedAt,
    androidStorageTotalBytes = androidStorageTotalBytes,
    androidStorageFreeBytes = androidStorageFreeBytes
)

fun FileEntry.toDto(): FileEntryDto = FileEntryDto(
    name = name,
    path = path,
    isDir = isDir,
    sizeBytes = sizeBytes,
    modifiedAt = modifiedAt,
    extension = extension
)

fun DirectoryListing.toDto(): DirectoryListingResponseDto = DirectoryListingResponseDto(
    currentPath = currentPath,
    hostname = hostname,
    entries = entries.map { it.toDto() }
)

fun StorageMetrics.toDto(): StorageResponseDto = StorageResponseDto(
    androidStorageTotalBytes = androidStorageTotalBytes,
    androidStorageFreeBytes = androidStorageFreeBytes
)

fun VerifiedUploadResult.toDto(): UploadVerifiedResponseDto = UploadVerifiedResponseDto(
    verified = verified,
    savedPath = savedPath,
    sizeBytes = sizeBytes,
    sha256 = sha256
)
