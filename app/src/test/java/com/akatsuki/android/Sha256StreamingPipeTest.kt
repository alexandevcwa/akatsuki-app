package com.akatsuki.android

import com.akatsuki.android.domain.model.FileIntegrityViolationException
import com.akatsuki.android.infrastructure.storage.Sha256StreamingPipe
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class Sha256StreamingPipeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `escribe stream multi-bloque de 64KB en archivo part verifica SHA-256 y renombra atomicamente`() = runTest {
        // Crear payload de 200 KB (> 3 bloques de 64 KB)
        val payloadBytes = ByteArray(200 * 1024) { index -> (index % 251).toByte() }
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(payloadBytes)
            .joinToString("") { "%02x".format(it) }

        val targetFile = File(tempFolder.root, "shinobi_archive.bin")
        val partFile = File(tempFolder.root, "shinobi_archive.bin.part")

        val result = Sha256StreamingPipe.streamWriteWithPartAndVerifySha256(
            targetFile = targetFile,
            expectedSha256 = expectedHash,
            inputStream = ByteArrayInputStream(payloadBytes)
        )

        assertTrue(result.verified)
        assertEquals(payloadBytes.size.toLong(), result.sizeBytes)
        assertEquals(expectedHash, result.sha256)
        assertTrue(targetFile.exists())
        assertFalse(partFile.exists())
    }

    @Test
    fun `elimina inmediatamente el archivo temporal part y lanza 422 cuando el hash SHA-256 no coincide`() = runTest {
        val payloadBytes = "Datos alterados en tránsito".toByteArray()
        val wrongExpectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val targetFile = File(tempFolder.root, "corrupted_payload.zip")
        val partFile = File(tempFolder.root, "corrupted_payload.zip.part")

        var exceptionThrown = false
        try {
            Sha256StreamingPipe.streamWriteWithPartAndVerifySha256(
                targetFile = targetFile,
                expectedSha256 = wrongExpectedHash,
                inputStream = ByteArrayInputStream(payloadBytes)
            )
        } catch (_: FileIntegrityViolationException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        assertFalse(targetFile.exists())
        assertFalse(partFile.exists())
    }
}
