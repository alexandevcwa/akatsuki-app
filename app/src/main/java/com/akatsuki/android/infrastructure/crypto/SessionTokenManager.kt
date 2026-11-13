package com.akatsuki.android.infrastructure.crypto

import com.akatsuki.android.domain.model.ActiveSession
import com.akatsuki.android.domain.model.DeviceIdentity
import com.akatsuki.android.domain.model.EphemeralPinState
import com.akatsuki.android.domain.model.StorageMetrics
import com.akatsuki.android.domain.model.TooManyPinAttemptsException
import com.akatsuki.android.domain.model.UnauthorizedAccessException
import com.akatsuki.android.domain.repository.SessionAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Capa 3 de Seguridad — Autenticación de Sesión mediante PIN Efímero + Token Bearer:
 * - Genera un PIN numérico de 6 dígitos que rota cada 5 minutos o bajo demanda.
 * - Protege contra ataques de fuerza bruta: tras 5 intentos fallidos consecutivos,
 *   aplica un bloqueo temporal de 60 segundos devolviendo 429 Too Many Requests.
 * - Emite un Bearer Token con estructura JWT firmada con HMAC-SHA256 (válido por 24h
 *   o hasta su revocación en POST /api/v1/auth/revoke).
 */
class SessionTokenManager(
    initialTimeMillis: Long = System.currentTimeMillis()
) : SessionAuthRepository {

    companion object {
        const val PIN_TTL_MILLIS = 5 * 60 * 1000L          // 5 minutos
        const val MAX_FAILED_ATTEMPTS = 5                  // Máximo 5 intentos fallidos
        const val LOCKOUT_DURATION_MILLIS = 60 * 1000L     // 60 segundos de bloqueo (429)
        const val SESSION_TTL_MILLIS = 24 * 60 * 60 * 1000L // 24 horas
    }

    private val secureRandom = SecureRandom()
    private val hmacSecret = ByteArray(32).also { secureRandom.nextBytes(it) }
    private val sessionsByToken = ConcurrentHashMap<String, StoredSessionRecord>()

    private val _pinState = MutableStateFlow(generateNewPinState(initialTimeMillis))
    override val pinState: StateFlow<EphemeralPinState> = _pinState.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<ActiveSession>>(emptyList())
    override val activeSessions: StateFlow<List<ActiveSession>> = _activeSessions.asStateFlow()

    private data class StoredSessionRecord(
        val session: ActiveSession,
        val expiresAtMillis: Long
    )

    @Synchronized
    override fun rotatePin(nowMillis: Long): EphemeralPinState {
        val currentLock = _pinState.value.lockedUntilMillis
        val newPin = generateNewPinState(nowMillis).copy(
            failedAttempts = if (nowMillis < currentLock) _pinState.value.failedAttempts else 0,
            lockedUntilMillis = if (nowMillis < currentLock) currentLock else 0L
        )
        _pinState.value = newPin
        return newPin
    }

    @Synchronized
    override fun verifyPinAndIssueSession(
        pinCode: String,
        clientName: String,
        deviceId: String,
        deviceName: String,
        model: String,
        ipAddress: String,
        port: Int,
        tlsCertFingerprintSha256: String,
        storageMetrics: StorageMetrics,
        nowMillis: Long
    ): ActiveSession {
        var currentPin = _pinState.value

        // 1. Verificar si hay bloqueo activo por fuerza bruta (429 Too Many Requests)
        if (currentPin.isLocked(nowMillis)) {
            val retryAfterSec = ((currentPin.lockedUntilMillis - nowMillis + 999L) / 1000L).coerceAtLeast(1L)
            throw TooManyPinAttemptsException(retryAfterSeconds = retryAfterSec)
        }

        // Si un bloqueo previo ya expiró, reiniciar el contador de fallos
        if (currentPin.lockedUntilMillis in 1..nowMillis) {
            currentPin = currentPin.copy(failedAttempts = 0, lockedUntilMillis = 0L)
            _pinState.value = currentPin
        }

        // 2. Verificar si el PIN expiró (5 minutos)
        if (currentPin.isExpired(nowMillis)) {
            val rotated = generateNewPinState(nowMillis)
            _pinState.value = rotated
            throw UnauthorizedAccessException("El PIN temporal ha expirado. Se ha generado un nuevo PIN en el dispositivo Android.")
        }

        // 3. Comparación en tiempo constante del PIN de 6 dígitos
        val isMatch = MessageDigest.isEqual(
            currentPin.pinCode.toByteArray(StandardCharsets.UTF_8),
            pinCode.trim().toByteArray(StandardCharsets.UTF_8)
        )

        if (!isMatch) {
            val newFailedCount = currentPin.failedAttempts + 1
            if (newFailedCount >= MAX_FAILED_ATTEMPTS) {
                val lockUntil = nowMillis + LOCKOUT_DURATION_MILLIS
                _pinState.value = currentPin.copy(
                    failedAttempts = newFailedCount,
                    lockedUntilMillis = lockUntil
                )
                throw TooManyPinAttemptsException(retryAfterSeconds = LOCKOUT_DURATION_MILLIS / 1000L)
            } else {
                _pinState.value = currentPin.copy(failedAttempts = newFailedCount)
                val remaining = MAX_FAILED_ATTEMPTS - newFailedCount
                throw UnauthorizedAccessException(
                    "Código PIN incorrecto. Intentos restantes antes de bloqueo temporal: $remaining."
                )
            }
        }

        // 4. Éxito: Rotar PIN usado para evitar replay y emitir Bearer Token JWT HMAC-SHA256
        _pinState.value = generateNewPinState(nowMillis)

        val connectedAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(nowMillis))
        val bearerToken = createSignedJwtToken(
            deviceId = deviceId,
            clientName = clientName,
            issuedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + SESSION_TTL_MILLIS
        )

        val deviceIdentity = DeviceIdentity(
            deviceId = deviceId,
            deviceName = deviceName,
            model = model,
            ipAddress = ipAddress,
            port = port,
            tlsCertFingerprintSha256 = tlsCertFingerprintSha256,
            discoveredVia = "https-tofu",
            paired = true
        )

        val activeSession = ActiveSession(
            device = deviceIdentity,
            bearerToken = bearerToken,
            clientName = clientName,
            connectedAt = connectedAtIso,
            androidStorageTotalBytes = storageMetrics.androidStorageTotalBytes,
            androidStorageFreeBytes = storageMetrics.androidStorageFreeBytes
        )

        sessionsByToken[bearerToken] = StoredSessionRecord(
            session = activeSession,
            expiresAtMillis = nowMillis + SESSION_TTL_MILLIS
        )
        syncSessionsFlow()

        return activeSession
    }

    override fun validateBearerToken(bearerToken: String?, nowMillis: Long): ActiveSession {
        val raw = bearerToken?.trim()
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")
            ?.trim()

        if (raw.isNullOrEmpty()) {
            throw UnauthorizedAccessException("Cabecera Authorization: Bearer <token> requerida.")
        }

        val record = sessionsByToken[raw]
            ?: throw UnauthorizedAccessException("Token de sesión inválido o revocado.")

        if (nowMillis >= record.expiresAtMillis || !verifySignedJwtToken(raw)) {
            sessionsByToken.remove(raw)
            syncSessionsFlow()
            throw UnauthorizedAccessException("La sesión ha expirado. Vuelva a emparejar el dispositivo.")
        }

        return record.session
    }

    override fun revokeBearerToken(bearerToken: String): Boolean {
        val normalized = bearerToken.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
        val removed = sessionsByToken.remove(normalized) != null
        if (removed) {
            syncSessionsFlow()
        }
        return removed
    }

    override fun revokeAllSessions() {
        sessionsByToken.clear()
        syncSessionsFlow()
    }

    private fun syncSessionsFlow() {
        _activeSessions.value = sessionsByToken.values.map { it.session }
    }

    private fun generateNewPinState(nowMillis: Long): EphemeralPinState {
        val number = secureRandom.nextInt(900_000) + 100_000 // Siempre 6 dígitos (100000..999999)
        return EphemeralPinState(
            pinCode = "%06d".format(number),
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + PIN_TTL_MILLIS,
            failedAttempts = 0,
            lockedUntilMillis = 0L
        )
    }

    private fun createSignedJwtToken(
        deviceId: String,
        clientName: String,
        issuedAtMillis: Long,
        expiresAtMillis: Long
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val headerJson = """{"alg":"HS256","typ":"JWT"}"""
        val nonce = ByteArray(12).also { secureRandom.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val safeClient = clientName.replace("\"", "")
        val payloadJson =
            """{"sub":"$deviceId","client":"$safeClient","iat":${issuedAtMillis / 1000},"exp":${expiresAtMillis / 1000},"jti":"$nonce"}"""

        val headerB64 = encoder.encodeToString(headerJson.toByteArray(StandardCharsets.UTF_8))
        val payloadB64 = encoder.encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$headerB64.$payloadB64"
        val signatureB64 = encoder.encodeToString(computeHmacSha256(signingInput))
        return "$signingInput.$signatureB64"
    }

    private fun verifySignedJwtToken(jwt: String): Boolean {
        val parts = jwt.split('.')
        if (parts.size != 3) return false
        val signingInput = "${parts[0]}.${parts[1]}"
        val expectedSig = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(computeHmacSha256(signingInput))
        return MessageDigest.isEqual(
            expectedSig.toByteArray(StandardCharsets.UTF_8),
            parts[2].toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun computeHmacSha256(data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }
}
