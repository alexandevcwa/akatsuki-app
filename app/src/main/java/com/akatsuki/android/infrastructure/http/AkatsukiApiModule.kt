package com.akatsuki.android.infrastructure.http

import android.os.Build
import com.akatsuki.android.domain.model.FileIntegrityViolationException
import com.akatsuki.android.domain.model.InvalidRequestParameterException
import com.akatsuki.android.domain.model.PathTraversalSecurityException
import com.akatsuki.android.domain.model.ResourceNotFoundException
import com.akatsuki.android.domain.model.TooManyPinAttemptsException
import com.akatsuki.android.domain.model.UnauthorizedAccessException
import com.akatsuki.android.domain.repository.NodeConfigRepository
import com.akatsuki.android.domain.repository.SessionAuthRepository
import com.akatsuki.android.domain.repository.TlsCertificateRepository
import com.akatsuki.android.domain.usecase.FileOperationsUseCases
import com.akatsuki.android.domain.usecase.ManagePinAndPairingUseCase
import com.akatsuki.android.infrastructure.http.dto.ApiErrorResponseDto
import com.akatsuki.android.infrastructure.http.dto.DeleteResponseDto
import com.akatsuki.android.infrastructure.http.dto.HealthResponseDto
import com.akatsuki.android.infrastructure.http.dto.MkdirResponseDto
import com.akatsuki.android.infrastructure.http.dto.PairRequestDto
import com.akatsuki.android.infrastructure.http.dto.PathActionRequestDto
import com.akatsuki.android.infrastructure.http.dto.RevokeResponseDto
import com.akatsuki.android.infrastructure.http.dto.toDto
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Configuración modular de Ktor que implementa el Catálogo Completo de Endpoints REST y Streaming
 * especificados en la Parte 3 de AKATSUKI_DESIGN_AND_ANDROID_API_SPEC.md.
 */
fun Application.configureAkatsukiApi(
    nodeConfigRepository: NodeConfigRepository,
    certificateRepository: TlsCertificateRepository,
    sessionAuthRepository: SessionAuthRepository,
    managePinAndPairingUseCase: ManagePinAndPairingUseCase,
    fileOperationsUseCases: FileOperationsUseCases
) {
    val jsonSerializer = Json {
        prettyPrint = false
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    install(AutoHeadResponse)

    install(ContentNegotiation) {
        json(jsonSerializer)
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Range)
        allowHeader("X-Destination-Path")
        allowHeader("X-Expected-SHA256")
        allowHeader("X-File-SHA256")
        exposeHeader("X-File-SHA256")
        exposeHeader(HttpHeaders.ContentDisposition)
        exposeHeader(HttpHeaders.ContentLength)
        exposeHeader(HttpHeaders.ContentRange)
    }

    install(StatusPages) {
        exception<UnauthorizedAccessException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiErrorResponseDto(
                    error = "UNAUTHORIZED",
                    message = cause.message,
                    statusCode = HttpStatusCode.Unauthorized.value
                )
            )
        }
        exception<TooManyPinAttemptsException> { call, cause ->
            call.response.header(HttpHeaders.RetryAfter, cause.retryAfterSeconds.toString())
            call.respond(
                HttpStatusCode.TooManyRequests,
                ApiErrorResponseDto(
                    error = "TOO_MANY_REQUESTS",
                    message = cause.message,
                    statusCode = HttpStatusCode.TooManyRequests.value,
                    retryAfterSeconds = cause.retryAfterSeconds
                )
            )
        }
        exception<PathTraversalSecurityException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ApiErrorResponseDto(
                    error = "FORBIDDEN_PATH_TRAVERSAL",
                    message = cause.message,
                    statusCode = HttpStatusCode.Forbidden.value
                )
            )
        }
        exception<ResourceNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiErrorResponseDto(
                    error = "NOT_FOUND",
                    message = cause.message,
                    statusCode = HttpStatusCode.NotFound.value
                )
            )
        }
        exception<FileIntegrityViolationException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiErrorResponseDto(
                    error = "SHA256_INTEGRITY_MISMATCH",
                    message = cause.message,
                    statusCode = HttpStatusCode.UnprocessableEntity.value
                )
            )
        }
        exception<InvalidRequestParameterException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponseDto(
                    error = "BAD_REQUEST",
                    message = cause.message,
                    statusCode = HttpStatusCode.BadRequest.value
                )
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponseDto(
                    error = "INTERNAL_SERVER_ERROR",
                    message = cause.message ?: "Error interno del servidor Android",
                    statusCode = HttpStatusCode.InternalServerError.value
                )
            )
        }
    }

    /**
     * Valida la cabecera Authorization: Bearer <token> exigida en todos los endpoints protegidos.
     */
    fun ApplicationCall.requireAuthenticatedToken(): String {
        val authHeader = request.header(HttpHeaders.Authorization)
        val session = sessionAuthRepository.validateBearerToken(authHeader)
        return session.bearerToken
    }

    routing {
        route("/api/v1") {

            // 3.1 GET /api/v1/health (Pública)
            get("/health") {
                val certBundle = certificateRepository.getOrCreateCertificateBundle()
                val releaseVersion = Build.VERSION.RELEASE ?: "15"
                call.respond(
                    HttpStatusCode.OK,
                    HealthResponseDto(
                        status = "online",
                        deviceId = nodeConfigRepository.deviceIdFlow.value,
                        deviceName = nodeConfigRepository.deviceNameFlow.value,
                        model = "Android $releaseVersion • Akatsuki WebService v1.0",
                        port = nodeConfigRepository.portFlow.value,
                        tlsCertFingerprintSha256 = certBundle.fingerprintSha256
                    )
                )
            }

            // 3.2 POST /api/v1/auth/pair (PIN de 6 dígitos)
            post("/auth/pair") {
                val requestDto = runCatching { call.receive<PairRequestDto>() }.getOrElse {
                    throw InvalidRequestParameterException("Cuerpo JSON inválido. Se requiere { clientName, pinCode }.")
                }
                val activeSession = managePinAndPairingUseCase.pairDevice(
                    clientName = requestDto.clientName,
                    pinCode = requestDto.pinCode
                )
                call.respond(HttpStatusCode.OK, activeSession.toDto())
            }

            // 3.7 POST /api/v1/auth/revoke (Bearer Token)
            post("/auth/revoke") {
                val token = call.requireAuthenticatedToken()
                managePinAndPairingUseCase.revokeSession(token)
                call.respond(HttpStatusCode.OK, RevokeResponseDto(revoked = true))
            }

            // 3.7 GET /api/v1/device/storage (Bearer Token)
            get("/device/storage") {
                call.requireAuthenticatedToken()
                val metrics = fileOperationsUseCases.getStorageMetrics()
                call.respond(HttpStatusCode.OK, metrics.toDto())
            }

            // 3.3 GET /api/v1/files/list?path=/storage/emulated/0 (Bearer Token)
            get("/files/list") {
                call.requireAuthenticatedToken()
                val requestedPath = call.request.queryParameters["path"] ?.takeIf { it.isNotBlank() } ?: "/storage/emulated/0"
                val listing = fileOperationsUseCases.listDirectory(requestedPath)
                call.respond(HttpStatusCode.OK, listing.toDto())
            }

            // 3.6 POST /api/v1/files/mkdir (Bearer Token)
            post("/files/mkdir") {
                call.requireAuthenticatedToken()
                val body = runCatching { call.receive<PathActionRequestDto>() }.getOrElse {
                    throw InvalidRequestParameterException("Se requiere un JSON con { \"path\": \"...\" }.")
                }
                val createdPath = fileOperationsUseCases.createDirectory(body.path)
                call.respond(
                    HttpStatusCode.Created,
                    MkdirResponseDto(created = true, path = createdPath)
                )
            }

            // 3.6 DELETE /api/v1/files/delete (Bearer Token)
            delete("/files/delete") {
                call.requireAuthenticatedToken()
                val queryPath = call.request.queryParameters["path"]
                val targetPath = if (!queryPath.isNullOrBlank()) {
                    queryPath
                } else {
                    val rawBody = runCatching { call.receiveText() }.getOrNull().orEmpty().trim()
                    if (rawBody.isEmpty()) {
                        throw InvalidRequestParameterException("Se requiere 'path' en el body JSON o query parameter.")
                    }
                    runCatching {
                        jsonSerializer.decodeFromString<PathActionRequestDto>(rawBody).path
                    }.getOrElse {
                        throw InvalidRequestParameterException("Formato JSON inválido para eliminar ruta.")
                    }
                }
                val deletedPath = fileOperationsUseCases.deleteEntry(targetPath)
                call.respond(
                    HttpStatusCode.OK,
                    DeleteResponseDto(deleted = true, path = deletedPath)
                )
            }

            // 3.4 GET /api/v1/files/download (Transferencia Android -> PC con Streaming 64KB + X-File-SHA256 + Range)
            get("/files/download") {
                call.requireAuthenticatedToken()
                val requestedPath = call.request.queryParameters["path"]
                    ?: throw InvalidRequestParameterException("El query parameter 'path' es obligatorio.")

                val (descriptor, file) = fileOperationsUseCases.prepareDownload(requestedPath)
                val totalSize = descriptor.sizeBytes

                // Cabeceras obligatorias de la especificación 3.4
                call.response.header("X-File-SHA256", descriptor.sha256)
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        descriptor.fileName
                    ).toString()
                )

                val rangeHeader = call.request.header(HttpHeaders.Range)
                val parsedRange = parseHttpByteRange(rangeHeader, totalSize)

                if (parsedRange != null) {
                    val (startOffset, endOffset) = parsedRange
                    val contentLength = (endOffset - startOffset + 1L).coerceAtLeast(0L)
                    call.response.header(HttpHeaders.ContentLength, contentLength.toString())
                    call.response.header(
                        HttpHeaders.ContentRange,
                        "bytes $startOffset-$endOffset/$totalSize"
                    )
                    call.respondOutputStream(
                        contentType = ContentType.Application.OctetStream,
                        status = HttpStatusCode.PartialContent
                    ) {
                        fileOperationsUseCases.executeDownloadStream(
                            descriptor = descriptor,
                            file = file,
                            startOffset = startOffset,
                            length = contentLength,
                            outputStream = this
                        )
                    }
                } else {
                    call.response.header(HttpHeaders.ContentLength, totalSize.toString())
                    call.respondOutputStream(
                        contentType = ContentType.Application.OctetStream,
                        status = HttpStatusCode.OK
                    ) {
                        fileOperationsUseCases.executeDownloadStream(
                            descriptor = descriptor,
                            file = file,
                            startOffset = 0L,
                            length = totalSize,
                            outputStream = this
                        )
                    }
                }
            }

            // 3.5 POST /api/v1/files/upload (Transferencia PC -> Android con Streaming 64KB + .part + SHA-256)
            post("/files/upload") {
                call.requireAuthenticatedToken()

                val rawDestinationHeader = call.request.header("X-Destination-Path")
                    ?: call.request.queryParameters["path"]
                val expectedSha256Header = call.request.header("X-Expected-SHA256")
                    ?: call.request.header("X-File-SHA256")
                val totalBytesHint = call.request.contentLength() ?: 0L
                val contentType = call.request.contentType()

                // Estrategia 1: Multipart/form-data
                if (contentType.match(ContentType.MultiPart.FormData)) {
                    val multipart = call.receiveMultipart()
                    var resolvedDestPath = rawDestinationHeader?.let { decodeUrlHeaderIfNeeded(it) }
                    var resolvedExpectedSha = expectedSha256Header
                    var uploadResultDto: Any? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                when (part.name) {
                                    "path", "destinationPath" -> resolvedDestPath = part.value
                                    "sha256", "expectedSha256" -> resolvedExpectedSha = part.value
                                }
                            }
                            is PartData.FileItem -> {
                                val targetPath = resolvedDestPath
                                    ?: part.originalFileName?.let { "/storage/emulated/0/Download/$it" }
                                    ?: throw InvalidRequestParameterException("Falta 'X-Destination-Path' o campo 'path'.")
                                val result = part.provider().toInputStream().use { inputStream ->
                                    fileOperationsUseCases.executeVerifiedUpload(
                                        destinationPath = targetPath,
                                        expectedSha256 = resolvedExpectedSha,
                                        totalBytesHint = totalBytesHint,
                                        inputStream = inputStream
                                    )
                                }
                                uploadResultDto = result.toDto()
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }

                    val finalDto = uploadResultDto
                        ?: throw InvalidRequestParameterException("No se encontró archivo binario en la petición multipart.")
                    call.respond(HttpStatusCode.OK, finalDto)
                } else {
                    // Estrategia 2: Stream binario directo (application/octet-stream) — Especificación 3.5
                    val destinationPath = rawDestinationHeader?.let { decodeUrlHeaderIfNeeded(it) }
                        ?: throw InvalidRequestParameterException("Cabecera 'X-Destination-Path' requerida para la subida.")

                    val byteReadChannel = call.receiveChannel()
                    val result = byteReadChannel.toInputStream().use { inputStream ->
                        fileOperationsUseCases.executeVerifiedUpload(
                            destinationPath = destinationPath,
                            expectedSha256 = expectedSha256Header,
                            totalBytesHint = totalBytesHint,
                            inputStream = inputStream
                        )
                    }
                    call.respond(HttpStatusCode.OK, result.toDto())
                }
            }
        }
    }
}

private fun decodeUrlHeaderIfNeeded(rawHeader: String): String {
    val trimmed = rawHeader.trim()
    return if (trimmed.contains('%')) {
        runCatching {
            URLDecoder.decode(trimmed, StandardCharsets.UTF_8.name())
        }.getOrDefault(trimmed)
    } else {
        trimmed
    }
}

/**
 * Parsea la cabecera HTTP `Range: bytes=start-end` (ej. `bytes=0-` o `bytes=1024-4095`).
 * Si es `bytes=0-` y el cliente desea el archivo completo desde 0, devuelve null para responder `200 OK`
 * salvo que empiece en un offset > 0 o especifique un final parcial, en cuyo caso devuelve `206 Partial Content`.
 */
private fun parseHttpByteRange(rangeHeader: String?, totalSize: Long): Pair<Long, Long>? {
    if (rangeHeader.isNullOrBlank() || totalSize <= 0L) return null
    val normalized = rangeHeader.trim()
    if (!normalized.startsWith("bytes=")) return null

    val rangeSpec = normalized.removePrefix("bytes=").split(',').firstOrNull()?.trim() ?: return null
    val parts = rangeSpec.split('-', limit = 2)
    if (parts.size != 2) return null

    val startStr = parts[0].trim()
    val endStr = parts[1].trim()

    val startOffset = startStr.toLongOrNull() ?: return null
    val endOffset = if (endStr.isEmpty()) {
        totalSize - 1L
    } else {
        endStr.toLongOrNull()?.coerceAtMost(totalSize - 1L) ?: return null
    }

    if (startOffset < 0L || startOffset >= totalSize || endOffset < startOffset) {
        return null
    }

    // Si es bytes=0- sin límite superior, responder 200 OK estándar con flujo completo
    if (startOffset == 0L && endStr.isEmpty()) {
        return null
    }

    return startOffset to endOffset
}
