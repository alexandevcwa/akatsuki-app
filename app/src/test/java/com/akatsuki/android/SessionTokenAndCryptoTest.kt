package com.akatsuki.android

import com.akatsuki.android.domain.model.StorageMetrics
import com.akatsuki.android.domain.model.TooManyPinAttemptsException
import com.akatsuki.android.domain.model.UnauthorizedAccessException
import com.akatsuki.android.infrastructure.crypto.SessionTokenManager
import com.akatsuki.android.infrastructure.crypto.TlsCertificateManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionTokenAndCryptoTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `genera certificado X509 autofirmado y huella SHA-256 en formato DER separado por dos puntos`() = runTest {
        val ksDir = tempFolder.newFolder("keystore_test")
        val certManager = TlsCertificateManager(ksDir)

        val bundle = certManager.getOrCreateCertificateBundle()
        assertNotNull(bundle.keyStore)
        // Formato esperado: 32 bytes hex separados por ':' -> 95 caracteres ("9F:86:D0:...")
        val parts = bundle.fingerprintSha256.split(':')
        assertEquals(32, parts.size)
        assertTrue(parts.all { it.length == 2 && it.all { ch -> ch.isDigit() || ch in 'A'..'F' } })
    }

    @Test
    fun `valida PIN efimero de 6 digitos emite JWT Bearer Token y permite revocarlo`() {
        val now = 1_700_000_000_000L
        val tokenManager = SessionTokenManager(initialTimeMillis = now)
        val activePin = tokenManager.pinState.value.pinCode
        assertEquals(6, activePin.length)

        val session = tokenManager.verifyPinAndIssueSession(
            pinCode = activePin,
            clientName = "Akatsuki Desktop (hack4u)",
            deviceId = "ak-node-android-01",
            deviceName = "NOTHING PHONE (2) // ITACHI",
            model = "Android 15",
            ipAddress = "192.168.1.88",
            port = 8443,
            tlsCertFingerprintSha256 = "9F:86:D0:81:88:4C",
            storageMetrics = StorageMetrics(274877906944L, 158913789952L),
            nowMillis = now + 1_000L
        )

        assertTrue(session.bearerToken.startsWith("eyJ"))
        val validated = tokenManager.validateBearerToken(
            bearerToken = "Bearer ${session.bearerToken}",
            nowMillis = now + 2_000L
        )
        assertEquals("Akatsuki Desktop (hack4u)", validated.clientName)

        assertTrue(tokenManager.revokeBearerToken(session.bearerToken))
        assertFalse(tokenManager.revokeBearerToken(session.bearerToken))
    }

    @Test(expected = TooManyPinAttemptsException::class)
    fun `bloquea con 429 Too Many Requests al quinto intento fallido consecutivo de PIN`() {
        val now = 1_700_000_000_000L
        val tokenManager = SessionTokenManager(initialTimeMillis = now)
        val wrongPin = if (tokenManager.pinState.value.pinCode == "000000") "999999" else "000000"

        for (attempt in 1..4) {
            try {
                tokenManager.verifyPinAndIssueSession(
                    pinCode = wrongPin,
                    clientName = "Attacker PC",
                    deviceId = "ak-node-01",
                    deviceName = "ITACHI",
                    model = "Android 15",
                    ipAddress = "192.168.1.88",
                    port = 8443,
                    tlsCertFingerprintSha256 = "AA:BB",
                    storageMetrics = StorageMetrics(100L, 50L),
                    nowMillis = now + attempt * 100L
                )
            } catch (_: UnauthorizedAccessException) {
                // Esperado en los primeros 4 intentos
            }
        }

        // El 5º intento fallido dispara TooManyPinAttemptsException (429)
        tokenManager.verifyPinAndIssueSession(
            pinCode = wrongPin,
            clientName = "Attacker PC",
            deviceId = "ak-node-01",
            deviceName = "ITACHI",
            model = "Android 15",
            ipAddress = "192.168.1.88",
            port = 8443,
            tlsCertFingerprintSha256 = "AA:BB",
            storageMetrics = StorageMetrics(100L, 50L),
            nowMillis = now + 500L
        )
    }
}
