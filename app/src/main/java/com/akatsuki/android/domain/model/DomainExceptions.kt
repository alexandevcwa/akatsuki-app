package com.akatsuki.android.domain.model

/**
 * Excepciones de dominio mapeadas directamente a códigos de estado HTTP
 * según la especificación de la Parte 2 y Parte 3.
 */
sealed class AkatsukiDomainException(override val message: String) : Exception(message)

/** 401 Unauthorized — PIN inválido/expirado o Bearer Token ausente/revocado */
class UnauthorizedAccessException(message: String) : AkatsukiDomainException(message)

/** 429 Too Many Requests — Más de 5 intentos fallidos de PIN (bloqueo temporal de 60 segundos) */
class TooManyPinAttemptsException(
    val retryAfterSeconds: Long,
    message: String = "Demasiados intentos fallidos de PIN. Bloqueo activo por ${retryAfterSeconds}s."
) : AkatsukiDomainException(message)

/** 403 Forbidden — Intento de Path Traversal (../) fuera de /storage/emulated/0 o falta de permisos */
class PathTraversalSecurityException(message: String) : AkatsukiDomainException(message)

/** 404 Not Found — Archivo o directorio no encontrado */
class ResourceNotFoundException(message: String) : AkatsukiDomainException(message)

/** 422 Unprocessable Entity — El hash SHA-256 calculado en streaming no coincide con el esperado */
class FileIntegrityViolationException(
    val expectedSha256: String,
    val computedSha256: String
) : AkatsukiDomainException(
    "Violación de integridad SHA-256: esperado=$expectedSha256, calculado=$computedSha256"
)

/** 400 Bad Request — Parámetros faltantes o malformados */
class InvalidRequestParameterException(message: String) : AkatsukiDomainException(message)
