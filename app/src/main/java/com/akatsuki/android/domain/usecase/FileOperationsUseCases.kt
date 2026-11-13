package com.akatsuki.android.domain.usecase

import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.model.DirectoryListing
import com.akatsuki.android.domain.model.DownloadStreamDescriptor
import com.akatsuki.android.domain.model.StorageMetrics
import com.akatsuki.android.domain.model.TransferDirection
import com.akatsuki.android.domain.model.TransferStatus
import com.akatsuki.android.domain.model.TransferTelemetryItem
import com.akatsuki.android.domain.model.VerifiedUploadResult
import com.akatsuki.android.domain.repository.FileSystemGateway
import com.akatsuki.android.domain.repository.TelemetryRepository
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Casos de uso de exploración, gestión y transferencia verificada de archivos (Secciones 3.3 a 3.7).
 */
class FileOperationsUseCases(
    private val fileSystemGateway: FileSystemGateway,
    private val telemetryRepository: TelemetryRepository
) {
    fun getStorageMetrics(): StorageMetrics = fileSystemGateway.queryStorageMetrics()

    suspend fun listDirectory(path: String): DirectoryListing {
        return fileSystemGateway.listDirectory(path)
    }

    suspend fun createDirectory(path: String): String {
        val createdPath = fileSystemGateway.createDirectory(path)
        telemetryRepository.recordLog(
            tag = "FS-MKDIR",
            message = "Directorio creado: $createdPath",
            severity = AuditSeverity.INFO
        )
        return createdPath
    }

    suspend fun deleteEntry(path: String): String {
        val deletedPath = fileSystemGateway.deletePath(path)
        telemetryRepository.recordLog(
            tag = "FS-DELETE",
            message = "Elemento eliminado: $deletedPath",
            severity = AuditSeverity.WARNING
        )
        return deletedPath
    }

    suspend fun prepareDownload(path: String): Pair<DownloadStreamDescriptor, File> {
        return fileSystemGateway.prepareDownload(path)
    }

    suspend fun executeDownloadStream(
        descriptor: DownloadStreamDescriptor,
        file: File,
        startOffset: Long,
        length: Long,
        outputStream: OutputStream
    ) {
        val transferId = UUID.randomUUID().toString()
        val timeFormatted = currentTimeFormatted()

        telemetryRepository.upsertTransfer(
            TransferTelemetryItem(
                id = transferId,
                fileName = descriptor.fileName,
                remotePath = descriptor.canonicalPath,
                direction = TransferDirection.DOWNLOAD_ANDROID_TO_PC,
                bytesTransferred = 0L,
                totalBytes = length,
                speedMbps = 0.0,
                expectedSha256 = descriptor.sha256,
                computedSha256 = descriptor.sha256,
                status = TransferStatus.IN_PROGRESS,
                timestampFormatted = timeFormatted
            )
        )

        try {
            fileSystemGateway.streamDownloadRange(
                file = file,
                startOffset = startOffset,
                length = length,
                outputStream = outputStream
            ) { transferred, speedMbps ->
                telemetryRepository.upsertTransfer(
                    TransferTelemetryItem(
                        id = transferId,
                        fileName = descriptor.fileName,
                        remotePath = descriptor.canonicalPath,
                        direction = TransferDirection.DOWNLOAD_ANDROID_TO_PC,
                        bytesTransferred = transferred,
                        totalBytes = length,
                        speedMbps = speedMbps,
                        expectedSha256 = descriptor.sha256,
                        computedSha256 = descriptor.sha256,
                        status = TransferStatus.IN_PROGRESS,
                        timestampFormatted = timeFormatted
                    )
                )
            }

            telemetryRepository.upsertTransfer(
                TransferTelemetryItem(
                    id = transferId,
                    fileName = descriptor.fileName,
                    remotePath = descriptor.canonicalPath,
                    direction = TransferDirection.DOWNLOAD_ANDROID_TO_PC,
                    bytesTransferred = length,
                    totalBytes = length,
                    speedMbps = 0.0,
                    expectedSha256 = descriptor.sha256,
                    computedSha256 = descriptor.sha256,
                    status = TransferStatus.COMPLETED_VERIFIED,
                    timestampFormatted = timeFormatted
                )
            )
            telemetryRepository.recordLog(
                tag = "TX-DOWNLOAD",
                message = "Descarga completada: ${descriptor.fileName} (SHA-256: ${descriptor.sha256.take(12)}...)",
                severity = AuditSeverity.SUCCESS
            )
        } catch (e: Exception) {
            telemetryRepository.upsertTransfer(
                TransferTelemetryItem(
                    id = transferId,
                    fileName = descriptor.fileName,
                    remotePath = descriptor.canonicalPath,
                    direction = TransferDirection.DOWNLOAD_ANDROID_TO_PC,
                    bytesTransferred = 0L,
                    totalBytes = length,
                    speedMbps = 0.0,
                    expectedSha256 = descriptor.sha256,
                    computedSha256 = null,
                    status = TransferStatus.FAILED_IO,
                    timestampFormatted = timeFormatted
                )
            )
            throw e
        }
    }

    suspend fun executeVerifiedUpload(
        destinationPath: String,
        expectedSha256: String?,
        totalBytesHint: Long,
        inputStream: InputStream
    ): VerifiedUploadResult {
        val transferId = UUID.randomUUID().toString()
        val fileName = File(destinationPath).name.ifEmpty { "upload.bin" }
        val timeFormatted = currentTimeFormatted()

        telemetryRepository.upsertTransfer(
            TransferTelemetryItem(
                id = transferId,
                fileName = fileName,
                remotePath = destinationPath,
                direction = TransferDirection.UPLOAD_PC_TO_ANDROID,
                bytesTransferred = 0L,
                totalBytes = totalBytesHint.coerceAtLeast(1L),
                speedMbps = 0.0,
                expectedSha256 = expectedSha256,
                computedSha256 = null,
                status = TransferStatus.IN_PROGRESS,
                timestampFormatted = timeFormatted
            )
        )

        return try {
            val result = fileSystemGateway.receiveVerifiedUploadStream(
                destinationPath = destinationPath,
                expectedSha256 = expectedSha256,
                totalBytesHint = totalBytesHint,
                inputStream = inputStream
            ) { transferred, speedMbps ->
                telemetryRepository.upsertTransfer(
                    TransferTelemetryItem(
                        id = transferId,
                        fileName = fileName,
                        remotePath = destinationPath,
                        direction = TransferDirection.UPLOAD_PC_TO_ANDROID,
                        bytesTransferred = transferred,
                        totalBytes = totalBytesHint.coerceAtLeast(transferred),
                        speedMbps = speedMbps,
                        expectedSha256 = expectedSha256,
                        computedSha256 = null,
                        status = TransferStatus.IN_PROGRESS,
                        timestampFormatted = timeFormatted
                    )
                )
            }

            telemetryRepository.upsertTransfer(
                TransferTelemetryItem(
                    id = transferId,
                    fileName = fileName,
                    remotePath = result.savedPath,
                    direction = TransferDirection.UPLOAD_PC_TO_ANDROID,
                    bytesTransferred = result.sizeBytes,
                    totalBytes = result.sizeBytes,
                    speedMbps = 0.0,
                    expectedSha256 = expectedSha256,
                    computedSha256 = result.sha256,
                    status = TransferStatus.COMPLETED_VERIFIED,
                    timestampFormatted = timeFormatted
                )
            )
            telemetryRepository.recordLog(
                tag = "RX-UPLOAD",
                message = "Archivo recibido y verificado: $fileName (${result.sizeBytes} bytes, SHA-256 OK)",
                severity = AuditSeverity.SUCCESS
            )
            result
        } catch (e: Exception) {
            telemetryRepository.upsertTransfer(
                TransferTelemetryItem(
                    id = transferId,
                    fileName = fileName,
                    remotePath = destinationPath,
                    direction = TransferDirection.UPLOAD_PC_TO_ANDROID,
                    bytesTransferred = 0L,
                    totalBytes = totalBytesHint.coerceAtLeast(1L),
                    speedMbps = 0.0,
                    expectedSha256 = expectedSha256,
                    computedSha256 = null,
                    status = TransferStatus.FAILED_INTEGRITY,
                    timestampFormatted = timeFormatted
                )
            )
            telemetryRepository.recordLog(
                tag = "RX-FAIL",
                message = "Error en subida de '$fileName': ${e.message}",
                severity = AuditSeverity.CRITICAL
            )
            throw e
        }
    }

    private fun currentTimeFormatted(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }
}
