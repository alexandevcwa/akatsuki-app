package com.akatsuki.android.infrastructure.storage

import com.akatsuki.android.domain.model.FileIntegrityViolationException
import com.akatsuki.android.domain.model.VerifiedUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale

/**
 * Capa 4 de Seguridad — Integridad de Archivos End-to-End (SHA-256 en Streaming):
 *
 * Implementa el Patrón Decorator sobre flujos binarios leyendo y escribiendo en bloques
 * exactos de 64 KB (65,536 bytes):
 * 1. En subidas (PC -> Android): escribe en un archivo temporal `.part` dentro del directorio
 *    destino mientras alimenta un `MessageDigest.getInstance("SHA-256")`. Al finalizar,
 *    compara el hash calculado con `X-Expected-SHA256` (o `X-File-SHA256`). Si coinciden,
 *    renombra atómicamente `.part` a su nombre final; si no coinciden, elimina el `.part`
 *    inmediatamente y lanza `FileIntegrityViolationException` (422 Unprocessable Entity).
 * 2. En descargas (Android -> PC): calcula el hash SHA-256 en bloques de 64 KB y transmite
 *    el contenido soportando `Range` (206 Partial Content).
 */
object Sha256StreamingPipe {
    const val CHUNK_SIZE_BYTES = 64 * 1024 // 64 KB

    suspend fun computeFileSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE_BYTES)
        FileInputStream(file).use { input ->
            var read = input.read(buffer)
            while (read != -1) {
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
                read = input.read(buffer)
            }
        }
        digest.digest().toHexLower()
    }

    suspend fun streamWriteWithPartAndVerifySha256(
        targetFile: File,
        expectedSha256: String?,
        inputStream: InputStream,
        onProgress: (bytesWritten: Long, speedMbps: Double) -> Unit = { _, _ -> }
    ): VerifiedUploadResult = withContext(Dispatchers.IO) {
        val parentDir = targetFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        if (partFile.exists()) {
            partFile.delete()
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE_BYTES)
        var totalBytesWritten = 0L
        val startTimeNanos = System.nanoTime()
        var lastReportMillis = 0L

        try {
            FileOutputStream(partFile).use { fos ->
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    if (bytesRead > 0) {
                        fos.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalBytesWritten += bytesRead

                        val nowMillis = System.currentTimeMillis()
                        if (nowMillis - lastReportMillis >= 120L) {
                            lastReportMillis = nowMillis
                            val elapsedSec = ((System.nanoTime() - startTimeNanos) / 1_000_000_000.0)
                                .coerceAtLeast(0.001)
                            val speedMbps = (totalBytesWritten / (1024.0 * 1024.0)) / elapsedSec
                            onProgress(totalBytesWritten, speedMbps)
                        }
                    }
                    bytesRead = inputStream.read(buffer)
                }
                fos.flush()
                runCatching { fos.fd.sync() }
            }

            val computedSha256 = digest.digest().toHexLower()
            val normalizedExpected = expectedSha256?.trim()?.lowercase(Locale.US)
                ?.takeIf { it.isNotEmpty() }

            if (normalizedExpected != null && normalizedExpected != computedSha256) {
                partFile.delete()
                throw FileIntegrityViolationException(
                    expectedSha256 = normalizedExpected,
                    computedSha256 = computedSha256
                )
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val renamed = partFile.renameTo(targetFile)
            if (!renamed) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }

            VerifiedUploadResult(
                verified = true,
                savedPath = targetFile.canonicalPath,
                sizeBytes = totalBytesWritten,
                sha256 = computedSha256
            )
        } catch (t: Throwable) {
            if (partFile.exists()) {
                partFile.delete()
            }
            throw t
        }
    }

    suspend fun streamFileRangeToOutput(
        file: File,
        startOffset: Long,
        length: Long,
        outputStream: OutputStream,
        onProgress: (bytesSent: Long, speedMbps: Double) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(CHUNK_SIZE_BYTES)
        var remaining = length
        var totalSent = 0L
        val startTimeNanos = System.nanoTime()
        var lastReportMillis = 0L

        RandomAccessFile(file, "r").use { raf ->
            if (startOffset > 0L) {
                raf.seek(startOffset)
            }
            while (remaining > 0L) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) break
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    remaining -= read
                    totalSent += read

                    val nowMillis = System.currentTimeMillis()
                    if (nowMillis - lastReportMillis >= 120L) {
                        lastReportMillis = nowMillis
                        val elapsedSec = ((System.nanoTime() - startTimeNanos) / 1_000_000_000.0)
                            .coerceAtLeast(0.001)
                        val speedMbps = (totalSent / (1024.0 * 1024.0)) / elapsedSec
                        onProgress(totalSent, speedMbps)
                    }
                }
            }
            outputStream.flush()
        }
    }

    private fun ByteArray.toHexLower(): String {
        val hexChars = "0123456789abcdef"
        val result = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            result[i * 2] = hexChars[v ushr 4]
            result[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(result)
    }
}
