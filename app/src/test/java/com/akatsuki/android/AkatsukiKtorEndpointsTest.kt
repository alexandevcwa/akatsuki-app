package com.akatsuki.android

import com.akatsuki.android.data.repository.InMemoryTelemetryRepository
import com.akatsuki.android.domain.repository.NetworkDiscoveryGateway
import com.akatsuki.android.domain.repository.NodeConfigRepository
import com.akatsuki.android.domain.usecase.FileOperationsUseCases
import com.akatsuki.android.domain.usecase.ManagePinAndPairingUseCase
import com.akatsuki.android.infrastructure.crypto.SessionTokenManager
import com.akatsuki.android.infrastructure.crypto.TlsCertificateManager
import com.akatsuki.android.infrastructure.http.configureAkatsukiApi
import com.akatsuki.android.infrastructure.http.dto.ActiveSessionResponseDto
import com.akatsuki.android.infrastructure.http.dto.DeleteResponseDto
import com.akatsuki.android.infrastructure.http.dto.DirectoryListingResponseDto
import com.akatsuki.android.infrastructure.http.dto.HealthResponseDto
import com.akatsuki.android.infrastructure.http.dto.MkdirResponseDto
import com.akatsuki.android.infrastructure.http.dto.RevokeResponseDto
import com.akatsuki.android.infrastructure.http.dto.StorageResponseDto
import com.akatsuki.android.infrastructure.http.dto.UploadVerifiedResponseDto
import com.akatsuki.android.infrastructure.storage.AndroidFileSystemGateway
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class AkatsukiKtorEndpointsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeNodeConfigRepository : NodeConfigRepository {
        override val deviceIdFlow: StateFlow<String> = MutableStateFlow("ak-node-android-01")
        override val deviceNameFlow: StateFlow<String> = MutableStateFlow("NOTHING PHONE (2) // ITACHI")
        override val portFlow: StateFlow<Int> = MutableStateFlow(8443)
        override suspend fun updateDeviceName(newName: String) {}
        override suspend fun updatePort(newPort: Int) {}
    }

    private class FakeNetworkDiscoveryGateway : NetworkDiscoveryGateway {
        override fun resolveLocalLanIpAddress(): String = "192.168.1.88"
        override fun publishMdnsService(
            serviceName: String,
            port: Int,
            fingerprintSha256: String,
            deviceId: String,
            onStatusChanged: (Boolean) -> Unit
        ) {
            onStatusChanged(true)
        }
        override fun unpublishMdnsService() {}
    }

    @Test
    fun `verifica ciclo completo de los 9 endpoints REST y Streaming de la especificacion`() = testApplication {
        val storageRoot = tempFolder.newFolder("storage", "emulated", "0")
        val ksDir = tempFolder.newFolder("keystore")

        val nodeConfigRepo = FakeNodeConfigRepository()
        val certRepo = TlsCertificateManager(ksDir)
        val sessionAuthRepo = SessionTokenManager()
        val telemetryRepo = InMemoryTelemetryRepository()
        val fileGateway = AndroidFileSystemGateway(customAllowedRoots = listOf(storageRoot.canonicalPath))
        val networkGateway = FakeNetworkDiscoveryGateway()

        val pairingUseCase = ManagePinAndPairingUseCase(
            sessionAuthRepository = sessionAuthRepo,
            certificateRepository = certRepo,
            fileSystemGateway = fileGateway,
            networkDiscoveryGateway = networkGateway,
            nodeConfigRepository = nodeConfigRepo,
            telemetryRepository = telemetryRepo
        )
        val fileUseCases = FileOperationsUseCases(
            fileSystemGateway = fileGateway,
            telemetryRepository = telemetryRepo
        )

        application {
            configureAkatsukiApi(
                nodeConfigRepository = nodeConfigRepo,
                certificateRepository = certRepo,
                sessionAuthRepository = sessionAuthRepo,
                managePinAndPairingUseCase = pairingUseCase,
                fileOperationsUseCases = fileUseCases
            )
        }

        // 1. GET /api/v1/health (Público)
        val healthResp = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, healthResp.status)
        val healthDto = json.decodeFromString<HealthResponseDto>(healthResp.bodyAsText())
        assertEquals("online", healthDto.status)
        assertEquals("ak-node-android-01", healthDto.deviceId)
        assertEquals(8443, healthDto.port)
        assertEquals(32, healthDto.tlsCertFingerprintSha256.split(':').size)

        // 2. Verificar que /api/v1/files/list sin Bearer Token devuelve 401 Unauthorized
        val unauthResp = client.get("/api/v1/files/list?path=${storageRoot.canonicalPath}")
        assertEquals(HttpStatusCode.Unauthorized, unauthResp.status)

        // 3. POST /api/v1/auth/pair (PIN de 6 dígitos)
        val currentPin = sessionAuthRepo.pinState.value.pinCode
        val pairResp = client.post("/api/v1/auth/pair") {
            contentType(ContentType.Application.Json)
            setBody("""{"clientName":"Akatsuki Desktop (hack4u)","pinCode":"$currentPin"}""")
        }
        assertEquals(HttpStatusCode.OK, pairResp.status)
        val sessionDto = json.decodeFromString<ActiveSessionResponseDto>(pairResp.bodyAsText())
        assertEquals("192.168.1.88", sessionDto.device.ipAddress)
        assertEquals("https-tofu", sessionDto.device.discoveredVia)
        assertTrue(sessionDto.device.paired)
        val bearerHeader = "Bearer ${sessionDto.bearerToken}"

        // 4. GET /api/v1/device/storage
        val storageResp = client.get("/api/v1/device/storage") {
            header(HttpHeaders.Authorization, bearerHeader)
        }
        assertEquals(HttpStatusCode.OK, storageResp.status)
        val storageDto = json.decodeFromString<StorageResponseDto>(storageResp.bodyAsText())
        assertTrue(storageDto.androidStorageTotalBytes > 0L)

        // 5. POST /api/v1/files/mkdir
        val newFolderPath = "${storageRoot.canonicalPath}/Download/Reportes"
        val mkdirResp = client.post("/api/v1/files/mkdir") {
            header(HttpHeaders.Authorization, bearerHeader)
            contentType(ContentType.Application.Json)
            setBody("""{"path":"$newFolderPath"}""")
        }
        assertEquals(HttpStatusCode.Created, mkdirResp.status)
        val mkdirDto = json.decodeFromString<MkdirResponseDto>(mkdirResp.bodyAsText())
        assertTrue(mkdirDto.created)

        // 6. POST /api/v1/files/upload (Stream binario + X-Destination-Path + X-Expected-SHA256)
        val fileBytes = ByteArray(150 * 1024) { idx -> (idx % 199).toByte() }
        val fileSha256 = MessageDigest.getInstance("SHA-256")
            .digest(fileBytes)
            .joinToString("") { "%02x".format(it) }
        val uploadedFilePath = "$newFolderPath/VID_20261112_4K60.mp4"

        val uploadResp = client.post("/api/v1/files/upload") {
            header(HttpHeaders.Authorization, bearerHeader)
            header("X-Destination-Path", uploadedFilePath)
            header("X-Expected-SHA256", fileSha256)
            contentType(ContentType.Application.OctetStream)
            setBody(fileBytes)
        }
        assertEquals(HttpStatusCode.OK, uploadResp.status)
        val uploadDto = json.decodeFromString<UploadVerifiedResponseDto>(uploadResp.bodyAsText())
        assertTrue(uploadDto.verified)
        assertEquals(fileSha256, uploadDto.sha256)
        assertEquals(fileBytes.size.toLong(), uploadDto.sizeBytes)

        // 7. GET /api/v1/files/list
        val listResp = client.get("/api/v1/files/list?path=$newFolderPath") {
            header(HttpHeaders.Authorization, bearerHeader)
        }
        assertEquals(HttpStatusCode.OK, listResp.status)
        val listDto = json.decodeFromString<DirectoryListingResponseDto>(listResp.bodyAsText())
        assertEquals(1, listDto.entries.size)
        assertEquals("VID_20261112_4K60.mp4", listDto.entries.first().name)
        assertEquals("mp4", listDto.entries.first().extension)

        // 8. GET /api/v1/files/download (Verifica X-File-SHA256 y bytes descargados)
        val downloadResp = client.get("/api/v1/files/download?path=$uploadedFilePath") {
            header(HttpHeaders.Authorization, bearerHeader)
        }
        assertEquals(HttpStatusCode.OK, downloadResp.status)
        assertEquals(fileSha256, downloadResp.headers["X-File-SHA256"])
        assertArrayEquals(fileBytes, downloadResp.bodyAsBytes())

        // 8b. GET /api/v1/files/download con cabecera Range parcial (206 Partial Content)
        val partialResp = client.get("/api/v1/files/download?path=$uploadedFilePath") {
            header(HttpHeaders.Authorization, bearerHeader)
            header(HttpHeaders.Range, "bytes=100-199")
        }
        assertEquals(HttpStatusCode.PartialContent, partialResp.status)
        assertArrayEquals(fileBytes.copyOfRange(100, 200), partialResp.bodyAsBytes())

        // 9. DELETE /api/v1/files/delete
        val deleteResp = client.delete("/api/v1/files/delete") {
            header(HttpHeaders.Authorization, bearerHeader)
            contentType(ContentType.Application.Json)
            setBody("""{"path":"$uploadedFilePath"}""")
        }
        assertEquals(HttpStatusCode.OK, deleteResp.status)
        val deleteDto = json.decodeFromString<DeleteResponseDto>(deleteResp.bodyAsText())
        assertTrue(deleteDto.deleted)

        // 10. POST /api/v1/auth/revoke
        val revokeResp = client.post("/api/v1/auth/revoke") {
            header(HttpHeaders.Authorization, bearerHeader)
        }
        assertEquals(HttpStatusCode.OK, revokeResp.status)
        val revokeDto = json.decodeFromString<RevokeResponseDto>(revokeResp.bodyAsText())
        assertTrue(revokeDto.revoked)

        // Token revocado ya no debe tener acceso (401)
        val afterRevokeResp = client.get("/api/v1/device/storage") {
            header(HttpHeaders.Authorization, bearerHeader)
        }
        assertEquals(HttpStatusCode.Unauthorized, afterRevokeResp.status)
    }
}
