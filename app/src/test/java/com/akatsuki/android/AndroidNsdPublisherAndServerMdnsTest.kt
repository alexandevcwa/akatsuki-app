package com.akatsuki.android

import com.akatsuki.android.data.repository.InMemoryTelemetryRepository
import com.akatsuki.android.domain.model.ServerLifecycleState
import com.akatsuki.android.domain.repository.NetworkDiscoveryGateway
import com.akatsuki.android.domain.repository.NodeConfigRepository
import com.akatsuki.android.domain.usecase.FileOperationsUseCases
import com.akatsuki.android.domain.usecase.ManagePinAndPairingUseCase
import com.akatsuki.android.infrastructure.crypto.SessionTokenManager
import com.akatsuki.android.infrastructure.crypto.TlsCertificateManager
import com.akatsuki.android.infrastructure.http.AkatsukiKtorServer
import com.akatsuki.android.infrastructure.network.AndroidNsdPublisher
import com.akatsuki.android.infrastructure.network.MdnsRegistrationRequest
import com.akatsuki.android.infrastructure.network.MulticastLockController
import com.akatsuki.android.infrastructure.network.MulticastLockHandle
import com.akatsuki.android.infrastructure.network.NetworkChangeMonitor
import com.akatsuki.android.infrastructure.network.NetworkInterfaceSnapshot
import com.akatsuki.android.infrastructure.network.NsdBackend
import com.akatsuki.android.infrastructure.network.NsdRegistrationCallbacks
import com.akatsuki.android.infrastructure.network.NsdRegistrationHandle
import com.akatsuki.android.infrastructure.storage.AndroidFileSystemGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

class AndroidNsdPublisherAndServerMdnsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeMulticastLockHandle : MulticastLockHandle {
        var releaseCount = 0
        override fun release() {
            releaseCount++
        }
    }

    private class FakeMulticastController : MulticastLockController {
        val locks = mutableListOf<FakeMulticastLockHandle>()
        override fun acquire(tag: String): MulticastLockHandle {
            return FakeMulticastLockHandle().also { locks.add(it) }
        }
    }

    private class FakeHandle(
        val id: Int,
        val request: MdnsRegistrationRequest,
        val callbacks: NsdRegistrationCallbacks
    ) : NsdRegistrationHandle

    private class FakeNsdBackend(
        var available: Boolean = true,
        var throwOnRegister: Throwable? = null,
        var autoConfirmUnregister: Boolean = true
    ) : NsdBackend {
        private var seq = 0
        val registeredHandles = CopyOnWriteArrayList<FakeHandle>()
        val unregisteredHandleIds = CopyOnWriteArrayList<Int>()

        override fun isAvailable(): Boolean = available

        override fun registerService(
            request: MdnsRegistrationRequest,
            callbacks: NsdRegistrationCallbacks
        ): NsdRegistrationHandle {
            throwOnRegister?.let { throw it }
            val handle = FakeHandle(++seq, request, callbacks)
            registeredHandles.add(handle)
            return handle
        }

        override fun unregisterService(handle: NsdRegistrationHandle) {
            val fake = handle as FakeHandle
            unregisteredHandleIds.add(fake.id)
            if (autoConfirmUnregister) {
                fake.callbacks.onUnregistered()
            }
        }
    }

    private class NoOpNetworkMonitor : NetworkChangeMonitor {
        override fun observe(onNetworkChanged: () -> Unit): AutoCloseable = AutoCloseable {}
    }

    private fun createPublisher(
        telemetry: InMemoryTelemetryRepository = InMemoryTelemetryRepository(),
        backend: FakeNsdBackend = FakeNsdBackend(),
        multicast: FakeMulticastController = FakeMulticastController(),
        interfaces: List<NetworkInterfaceSnapshot> = emptyList()
    ): AndroidNsdPublisher {
        return AndroidNsdPublisher(
            telemetryRepository = telemetry,
            nsdBackend = backend,
            multicastController = multicast,
            networkMonitor = NoOpNetworkMonitor(),
            interfaceSnapshotProvider = { interfaces }
        )
    }

    @Test
    fun `publica servicio mDNS con TXT validos y libera MulticastLock al despublicar`() {
        val telemetry = InMemoryTelemetryRepository()
        val backend = FakeNsdBackend()
        val multicast = FakeMulticastController()
        val publisher = createPublisher(telemetry, backend, multicast)

        val events = mutableListOf<Pair<Boolean, String?>>()
        publisher.publishMdnsService(
            serviceName = "AkatsukiNode",
            port = 8443,
            fingerprintSha256 = "9F:86:D0:81:88:4C:7D:65:9A:2F:EA:A0:C5:5A:D0:15:A3:BF:4F:1B:2B:0B:82:2C:D1:5D:6C:15:B0:F0:0A:08",
            deviceId = "ak-node-android-01",
            deviceName = "NOTHING PHONE (2) // ITACHI"
        ) { published, resolvedName ->
            events.add(published to resolvedName)
        }

        assertEquals(1, backend.registeredHandles.size)
        val handle = backend.registeredHandles.first()
        assertEquals("AkatsukiNode", handle.request.serviceName)
        assertEquals("_akatsuki._tcp.", handle.request.serviceType)
        assertEquals(8443, handle.request.port)
        assertEquals(95, handle.request.txtAttributes["fp"]?.length)
        assertEquals("ak-node-android-01", handle.request.txtAttributes["id"])
        assertEquals("NOTHING PHONE (2) // ITACHI", handle.request.txtAttributes["name"])
        assertEquals(1, multicast.locks.size)
        assertEquals(0, multicast.locks.first().releaseCount)

        handle.callbacks.onRegistered("AkatsukiNode")
        assertEquals(listOf(true to "AkatsukiNode"), events)

        publisher.unpublishMdnsService()
        assertEquals(listOf(handle.id), backend.unregisteredHandleIds)
        assertEquals(1, multicast.locks.first().releaseCount)
        assertEquals(listOf(true to "AkatsukiNode"), events)
    }

    @Test
    fun `libera MulticastLock de inmediato cuando onRegistrationFailed ocurre`() {
        val telemetry = InMemoryTelemetryRepository()
        val backend = FakeNsdBackend()
        val multicast = FakeMulticastController()
        val publisher = createPublisher(telemetry, backend, multicast)

        val statuses = mutableListOf<Boolean>()
        publisher.publishMdnsService(
            serviceName = "AkatsukiNode",
            port = 8443,
            fingerprintSha256 = "AA:BB:CC",
            deviceId = "ak-01"
        ) { statuses.add(it) }

        val handle = backend.registeredHandles.single()
        handle.callbacks.onRegistrationFailed(errorCode = 3)

        assertEquals(listOf(false), statuses)
        assertEquals(1, multicast.locks.single().releaseCount)

        // Unpublish posterior no debe invocar unregisterService sobre un registro fallido
        publisher.unpublishMdnsService()
        assertTrue(backend.unregisteredHandleIds.isEmpty())
    }

    @Test
    fun `libera MulticastLock si registerService lanza excepcion sincronica`() {
        val telemetry = InMemoryTelemetryRepository()
        val backend = FakeNsdBackend(throwOnRegister = IllegalStateException("NSD daemon down"))
        val multicast = FakeMulticastController()
        val publisher = createPublisher(telemetry, backend, multicast)

        val statuses = mutableListOf<Boolean>()
        publisher.publishMdnsService(
            serviceName = "AkatsukiNode",
            port = 8443,
            fingerprintSha256 = "AA:BB:CC",
            deviceId = "ak-01"
        ) { statuses.add(it) }

        assertEquals(listOf(false), statuses)
        assertEquals(1, multicast.locks.single().releaseCount)
        assertTrue(telemetry.auditLogs.value.any { it.tag == "MDNS-ERR" })
    }

    @Test
    fun `no adquiere MulticastLock cuando NsdManager no esta disponible`() {
        val telemetry = InMemoryTelemetryRepository()
        val backend = FakeNsdBackend(available = false)
        val multicast = FakeMulticastController()
        val publisher = createPublisher(telemetry, backend, multicast)

        val statuses = mutableListOf<Boolean>()
        publisher.publishMdnsService(
            serviceName = "AkatsukiNode",
            port = 8443,
            fingerprintSha256 = "AA:BB:CC",
            deviceId = "ak-01"
        ) { statuses.add(it) }

        assertEquals(listOf(false), statuses)
        assertTrue(multicast.locks.isEmpty())
    }

    @Test
    fun `callback tardio onRegistered tras unpublish no reporta activo y limpia el registro`() {
        val telemetry = InMemoryTelemetryRepository()
        val backend = FakeNsdBackend(autoConfirmUnregister = false)
        val multicast = FakeMulticastController()
        val publisher = createPublisher(telemetry, backend, multicast)

        val events = mutableListOf<Boolean>()
        publisher.publishMdnsService(
            serviceName = "AkatsukiNode",
            port = 8443,
            fingerprintSha256 = "AA:BB:CC",
            deviceId = "ak-01"
        ) { events.add(it) }

        val handle = backend.registeredHandles.single()
        publisher.unpublishMdnsService()
        assertEquals(1, multicast.locks.single().releaseCount)
        assertEquals(listOf(handle.id), backend.unregisteredHandleIds)

        // Simula llegada tardía de onServiceRegistered después de haber detenido mDNS
        handle.callbacks.onRegistered("AkatsukiNode")
        assertTrue("Un callback obsoleto nunca debe reportar mDNS activo", events.isEmpty())
    }

    @Test
    fun `reemplazo de sesion mDNS evita que onUnregistered de sesion previa apague la nueva`() {
        val telemetry = InMemoryTelemetryRepository()
        val backend = FakeNsdBackend(autoConfirmUnregister = false)
        val multicast = FakeMulticastController()
        val publisher = createPublisher(telemetry, backend, multicast)

        val events1 = mutableListOf<Boolean>()
        publisher.publishMdnsService(
            serviceName = "AkatsukiNode",
            port = 8443,
            fingerprintSha256 = "AA:BB",
            deviceId = "ak-01"
        ) { events1.add(it) }
        val handle1 = backend.registeredHandles[0]
        handle1.callbacks.onRegistered("AkatsukiNode")

        val events2 = mutableListOf<Boolean>()
        val publishThread = Thread {
            publisher.publishMdnsService(
                serviceName = "AkatsukiNode",
                port = 9443,
                fingerprintSha256 = "CC:DD",
                deviceId = "ak-01"
            ) { events2.add(it) }
        }
        publishThread.start()

        // Esperar a que se solicite el desregistro de handle1 y luego confirmarlo desde otro hilo
        val deadline = System.currentTimeMillis() + 1000L
        while (!backend.unregisteredHandleIds.contains(handle1.id) && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        assertTrue(backend.unregisteredHandleIds.contains(handle1.id))
        handle1.callbacks.onUnregistered()
        publishThread.join(2000L)

        assertEquals(2, backend.registeredHandles.size)
        val handle2 = backend.registeredHandles[1]
        handle2.callbacks.onRegistered("AkatsukiNode")

        // Callback tardío de handle1 no debe afectar events2 ni events1
        handle1.callbacks.onUnregistered()

        assertEquals(listOf(true), events1)
        assertEquals(listOf(true), events2)
        assertEquals(1, multicast.locks[0].releaseCount)
        assertEquals(0, multicast.locks[1].releaseCount)
    }

    @Test
    fun `trunca atributo TXT name sin superar 64 bytes UTF-8 ni romper emojis`() {
        val longName = "NODO-" + "🔥".repeat(30) + "\nLINEA2"
        val attrs = AndroidNsdPublisher.buildTxtAttributes(
            fingerprintSha256 = "AA:BB:CC",
            deviceId = "ak-01",
            deviceName = longName
        )
        val encodedName = attrs["name"]
        assertNotNull(encodedName)
        assertTrue(encodedName!!.toByteArray(Charsets.UTF_8).size <= 64)
        assertFalse(encodedName.contains("\n"))
    }

    @Test
    fun `selectBestLanIpv4Address prioriza wlan ignora celular VPN y link-local`() {
        fun ipv4(ip: String): Inet4Address = InetAddress.getByName(ip) as Inet4Address

        val interfacesWithCellularAndVpnOnly = listOf(
            NetworkInterfaceSnapshot("lo", isUp = true, isLoopback = true, isPointToPoint = false, listOf(ipv4("127.0.0.1"))),
            NetworkInterfaceSnapshot("rmnet_data0", isUp = true, isLoopback = false, isPointToPoint = false, listOf(ipv4("10.123.45.6"))),
            NetworkInterfaceSnapshot("tun0", isUp = true, isLoopback = false, isPointToPoint = true, listOf(ipv4("10.8.0.2"))),
            NetworkInterfaceSnapshot("wlan0", isUp = true, isLoopback = false, isPointToPoint = false, listOf(ipv4("169.254.12.34")))
        )
        assertEquals("127.0.0.1", AndroidNsdPublisher.selectBestLanIpv4Address(interfacesWithCellularAndVpnOnly))

        val interfacesWithHotspot = interfacesWithCellularAndVpnOnly +
            NetworkInterfaceSnapshot("ap0", isUp = true, isLoopback = false, isPointToPoint = false, listOf(ipv4("192.168.43.1")))
        assertEquals("192.168.43.1", AndroidNsdPublisher.selectBestLanIpv4Address(interfacesWithHotspot))

        val interfacesWithWifi = interfacesWithHotspot +
            NetworkInterfaceSnapshot("wlan1", isUp = true, isLoopback = false, isPointToPoint = false, listOf(ipv4("192.168.1.88")))
        assertEquals("192.168.1.88", AndroidNsdPublisher.selectBestLanIpv4Address(interfacesWithWifi))
    }

    @Test
    fun `AkatsukiKtorServer espera DataStore evita resurreccion tras stop y republica al reiniciar o cambiar IP`() = runTest {
        val storageRoot = tempFolder.newFolder("storage", "emulated", "0")
        val ksDir = tempFolder.newFolder("keystore")

        val freePort1 = ServerSocket(0).use { it.localPort }
        val freePort2 = ServerSocket(0).use { it.localPort }

        val initGate = CompletableDeferred<Unit>()
        val fakeConfig = object : NodeConfigRepository {
            private val _id = MutableStateFlow("ak-default")
            private val _name = MutableStateFlow("DEFAULT")
            private val _port = MutableStateFlow(8443)
            override val deviceIdFlow: StateFlow<String> = _id
            override val deviceNameFlow: StateFlow<String> = _name
            override val portFlow: StateFlow<Int> = _port

            override suspend fun awaitInitialized() {
                initGate.await()
            }

            override suspend fun updateDeviceName(newName: String) {
                _name.value = newName
            }

            override suspend fun updatePort(newPort: Int) {
                _port.value = newPort
            }
        }

        // Simulamos DataStore tardando en cargar valores persistidos
        launch {
            delay(50L)
            fakeConfig.updateDeviceName("PIXEL 9 PRO")
            fakeConfig.updatePort(freePort1)
            initGate.complete(Unit)
        }

        class ControllableGateway : NetworkDiscoveryGateway {
            var currentIp = "192.168.1.50"
            val publishedRecords = mutableListOf<Triple<Int, String, String?>>()
            var unpublishCount = 0
            var lastStatusCallback: ((Boolean, String?) -> Unit)? = null
            var networkChangedCallback: (() -> Unit)? = null
            var observerClosed = false

            override fun resolveLocalLanIpAddress(): String = currentIp

            override fun publishMdnsService(
                serviceName: String,
                port: Int,
                fingerprintSha256: String,
                deviceId: String,
                onStatusChanged: (Boolean) -> Unit
            ) {
                publishMdnsService(serviceName, port, fingerprintSha256, deviceId, null) { pub, _ ->
                    onStatusChanged(pub)
                }
            }

            override fun publishMdnsService(
                serviceName: String,
                port: Int,
                fingerprintSha256: String,
                deviceId: String,
                deviceName: String?,
                onStatusChanged: (Boolean, String?) -> Unit
            ) {
                publishedRecords.add(Triple(port, fingerprintSha256, deviceName))
                lastStatusCallback = onStatusChanged
                observerClosed = false
            }

            override fun observeNetworkChanges(onNetworkChanged: () -> Unit): AutoCloseable {
                networkChangedCallback = onNetworkChanged
                return AutoCloseable {
                    observerClosed = true
                    networkChangedCallback = null
                }
            }

            override fun unpublishMdnsService() {
                unpublishCount++
            }
        }

        val certRepo = TlsCertificateManager(ksDir)
        val sessionAuthRepo = SessionTokenManager()
        val telemetryRepo = InMemoryTelemetryRepository()
        val fileGateway = AndroidFileSystemGateway(customAllowedRoots = listOf(storageRoot.canonicalPath))
        val networkGateway = ControllableGateway()

        val pairingUseCase = ManagePinAndPairingUseCase(
            sessionAuthRepository = sessionAuthRepo,
            certificateRepository = certRepo,
            fileSystemGateway = fileGateway,
            networkDiscoveryGateway = networkGateway,
            nodeConfigRepository = fakeConfig,
            telemetryRepository = telemetryRepo
        )
        val fileUseCases = FileOperationsUseCases(
            fileSystemGateway = fileGateway,
            telemetryRepository = telemetryRepo
        )

        val server = AkatsukiKtorServer(
            nodeConfigRepository = fakeConfig,
            certificateRepository = certRepo,
            sessionAuthRepository = sessionAuthRepo,
            networkDiscoveryGateway = networkGateway,
            telemetryRepository = telemetryRepo,
            managePinAndPairingUseCase = pairingUseCase,
            fileOperationsUseCases = fileUseCases
        )

        try {
            val started = server.startServer()
            assertTrue(started is ServerLifecycleState.Running)
            val running = started as ServerLifecycleState.Running
            assertEquals(freePort1, running.port)
            assertEquals(1, networkGateway.publishedRecords.size)
            assertEquals(freePort1, networkGateway.publishedRecords[0].first)
            assertEquals("PIXEL 9 PRO", networkGateway.publishedRecords[0].third)

            // Confirmar mDNS publicado
            val staleCallback = networkGateway.lastStatusCallback!!
            staleCallback(true, "AkatsukiNode")
            val activeState = telemetryRepo.serverState.value as ServerLifecycleState.Running
            assertTrue(activeState.nsdPublished)
            assertEquals("AkatsukiNode", activeState.nsdServiceName)

            // Cambio de IP en LAN debe actualizar el estado y republicar mDNS automáticamente
            networkGateway.currentIp = "192.168.2.99"
            networkGateway.networkChangedCallback?.invoke()
            withContext(Dispatchers.IO) {
                val deadline = System.currentTimeMillis() + 3000L
                while (System.currentTimeMillis() < deadline) {
                    val state = telemetryRepo.serverState.value as? ServerLifecycleState.Running
                    if (state?.ipAddress == "192.168.2.99" && networkGateway.publishedRecords.size >= 2) break
                    delay(25L)
                }
            }
            val updatedNetState = telemetryRepo.serverState.value as ServerLifecycleState.Running
            assertEquals("192.168.2.99", updatedNetState.ipAddress)
            assertEquals(2, networkGateway.publishedRecords.size)

            // Cambio de puerto + restartIfRunning debe republicar con el nuevo puerto
            fakeConfig.updatePort(freePort2)
            val restartedState = server.restartIfRunning()
            assertTrue("Se esperaba Running tras restartIfRunning pero fue: $restartedState", restartedState is ServerLifecycleState.Running)
            val restarted = restartedState as ServerLifecycleState.Running
            assertEquals(freePort2, restarted.port)
            assertEquals(3, networkGateway.publishedRecords.size)
            assertEquals(freePort2, networkGateway.publishedRecords[2].first)

            // Callback obsoleto de la sesión 1 tras detener el servidor NO debe revivir Running
            server.stopServer()
            assertEquals(ServerLifecycleState.Stopped, telemetryRepo.serverState.value)
            assertTrue(networkGateway.observerClosed)
            staleCallback(true, "AkatsukiNode")
            assertEquals(ServerLifecycleState.Stopped, telemetryRepo.serverState.value)

            // restartIfRunning cuando está detenido no debe iniciar el servidor
            val stillStopped = server.restartIfRunning()
            assertEquals(ServerLifecycleState.Stopped, stillStopped)
        } finally {
            server.stopServer()
        }
    }
}
