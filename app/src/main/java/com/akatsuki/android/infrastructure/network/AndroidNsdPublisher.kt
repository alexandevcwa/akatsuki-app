package com.akatsuki.android.infrastructure.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.repository.NetworkDiscoveryGateway
import com.akatsuki.android.domain.repository.TelemetryRepository
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal interface MulticastLockHandle {
    fun release()
}

internal interface MulticastLockController {
    fun acquire(tag: String): MulticastLockHandle?
}

internal interface NsdRegistrationHandle

internal interface NsdRegistrationCallbacks {
    fun onRegistered(resolvedServiceName: String)
    fun onRegistrationFailed(errorCode: Int)
    fun onUnregistered()
    fun onUnregistrationFailed(errorCode: Int)
}

internal data class MdnsRegistrationRequest(
    val serviceName: String,
    val serviceType: String,
    val port: Int,
    val txtAttributes: Map<String, String>
)

internal interface NsdBackend {
    fun isAvailable(): Boolean
    fun registerService(
        request: MdnsRegistrationRequest,
        callbacks: NsdRegistrationCallbacks
    ): NsdRegistrationHandle

    fun unregisterService(handle: NsdRegistrationHandle)
}

internal interface NetworkChangeMonitor {
    fun observe(onNetworkChanged: () -> Unit): AutoCloseable
}

internal data class NetworkInterfaceSnapshot(
    val name: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isPointToPoint: Boolean,
    val ipv4Addresses: List<Inet4Address>
)

/**
 * Publicación de Servicio mDNS/NSD y Resolución de IP LAN (Parte 2 y Parte 4.4):
 * - En la red LAN (estándar DNS-SD / Rust `mdns-sd`) el servicio se anuncia sobre UDP 5353 como:
 *   - FQDN: `_akatsuki._tcp.local.` (`AkatsukiNode._akatsuki._tcp.local.`)
 *   - Puerto SRV destino: puerto HTTPS configurado (8443 por defecto)
 *   - Atributos TXT: "fp" (huella SHA-256), "id" (deviceId), "name" (alias opcional)
 * - Nota de Android `NsdManager`: `NsdServiceInfo.serviceType` NO debe incluir `.local.` porque
 *   Android agrega el dominio `.local.` automáticamente; `NsdServiceInfo.port` corresponde al puerto
 *   del servicio HTTPS anunciado (no al puerto UDP 5353 del daemon mDNS del sistema).
 */
class AndroidNsdPublisher internal constructor(
    private val telemetryRepository: TelemetryRepository,
    private val nsdBackend: NsdBackend,
    private val multicastController: MulticastLockController,
    private val networkMonitor: NetworkChangeMonitor,
    private val interfaceSnapshotProvider: () -> List<NetworkInterfaceSnapshot>
) : NetworkDiscoveryGateway {

    constructor(
        context: Context,
        telemetryRepository: TelemetryRepository
    ) : this(
        telemetryRepository = telemetryRepository,
        nsdBackend = AndroidNsdBackend(context.applicationContext),
        multicastController = AndroidMulticastLockController(context.applicationContext),
        networkMonitor = AndroidNetworkChangeMonitor(context.applicationContext),
        interfaceSnapshotProvider = { collectSystemNetworkInterfaces() }
    )

    companion object {
        const val SERVICE_NAME_DEFAULT = "AkatsukiNode"
        const val MDNS_STANDARD_PORT = 5353
        // IMPORTANTE: En Android NsdServiceInfo.setServiceType NO debe llevar punto final ni .local
        const val SERVICE_TYPE = "_akatsuki._tcp"
        const val SERVICE_TYPE_FQDN = "_akatsuki._tcp.local."
        private const val MULTICAST_LOCK_TAG = "akatsuki_mdns_lock"
        private const val UNREGISTER_WAIT_TIMEOUT_MS = 600L
        private const val MAX_TXT_FP_BYTES = 240
        private const val MAX_TXT_ID_BYTES = 64
        private const val MAX_TXT_NAME_BYTES = 64

        internal fun normalizeAndroidServiceType(rawType: String): String {
            val trimmed = rawType.trim().lowercase().trimEnd('.')
            if (trimmed.isEmpty()) return SERVICE_TYPE
            val withoutLocal = when {
                trimmed.endsWith(".local") -> trimmed.removeSuffix(".local")
                else -> trimmed
            }.trimEnd('.')
            return withoutLocal.ifEmpty { SERVICE_TYPE }
        }

        private val EXCLUDED_INTERFACE_PREFIXES = listOf(
            "rmnet", "r_rmnet", "ccmni", "wwan", "pdp", "seth", "epdg",
            "tun", "tap", "ppp", "ipsec", "wg", "tailscale",
            "dummy", "clat", "v4-", "ifb", "sit", "lo", "p2p"
        )

        internal fun selectBestLanIpv4Address(interfaces: List<NetworkInterfaceSnapshot>): String {
            val candidates = interfaces
                .filter { iface ->
                    iface.isUp &&
                        !iface.isLoopback &&
                        !iface.isPointToPoint &&
                        !isExcludedInterface(iface.name)
                }
                .sortedBy { interfacePriority(it.name) }

            for (iface in candidates) {
                val priority = interfacePriority(iface.name)
                val validAddresses = iface.ipv4Addresses.filter { addr ->
                    !addr.isLoopbackAddress &&
                        !addr.isLinkLocalAddress &&
                        !addr.isMulticastAddress &&
                        !addr.isAnyLocalAddress
                }
                val preferred = validAddresses.firstOrNull { it.isSiteLocalAddress }
                    ?: if (priority < 4) validAddresses.firstOrNull() else null
                val host = preferred?.hostAddress
                if (!host.isNullOrBlank()) {
                    return host
                }
            }
            return "127.0.0.1"
        }

        internal fun buildTxtAttributes(
            fingerprintSha256: String,
            deviceId: String,
            deviceName: String?
        ): Map<String, String> {
            val attrs = linkedMapOf<String, String>()
            val cleanFp = sanitizeTxtValue(fingerprintSha256, MAX_TXT_FP_BYTES)
            if (cleanFp.isNotEmpty()) {
                attrs["fp"] = cleanFp
            }
            val cleanId = sanitizeTxtValue(deviceId, MAX_TXT_ID_BYTES)
            if (cleanId.isNotEmpty()) {
                attrs["id"] = cleanId
            }
            val cleanName = deviceName?.let { sanitizeTxtValue(it, MAX_TXT_NAME_BYTES) }.orEmpty()
            if (cleanName.isNotEmpty()) {
                attrs["name"] = cleanName
            }
            return attrs
        }

        private fun isExcludedInterface(rawName: String): Boolean {
            val lower = rawName.lowercase()
            return EXCLUDED_INTERFACE_PREFIXES.any { lower.startsWith(it) }
        }

        private fun interfacePriority(rawName: String): Int {
            val name = rawName.lowercase()
            return when {
                name.startsWith("wlan") -> 0
                name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 1
                name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("ncm") -> 2
                name.startsWith("eth") || name.startsWith("en") -> 3
                else -> 4
            }
        }

        private fun sanitizeTxtValue(raw: String, maxUtf8Bytes: Int): String {
            val cleaned = raw
                .replace("\r", "")
                .replace("\n", " ")
                .replace("\u0000", "")
                .trim()
            if (cleaned.isEmpty()) return ""
            return truncateUtf8Bytes(cleaned, maxUtf8Bytes)
        }

        private fun truncateUtf8Bytes(value: String, maxBytes: Int): String {
            if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
            val sb = StringBuilder()
            var usedBytes = 0
            var index = 0
            while (index < value.length) {
                val codePoint = value.codePointAt(index)
                val charCount = Character.charCount(codePoint)
                val slice = value.substring(index, index + charCount)
                val sliceBytes = slice.toByteArray(Charsets.UTF_8).size
                if (usedBytes + sliceBytes > maxBytes) break
                sb.append(slice)
                usedBytes += sliceBytes
                index += charCount
            }
            return sb.toString()
        }

        private fun collectSystemNetworkInterfaces(): List<NetworkInterfaceSnapshot> {
            return runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { netIf ->
                    NetworkInterfaceSnapshot(
                        name = netIf.name.orEmpty(),
                        isUp = runCatching { netIf.isUp }.getOrDefault(false),
                        isLoopback = runCatching { netIf.isLoopback }.getOrDefault(false),
                        isPointToPoint = runCatching { netIf.isPointToPoint }.getOrDefault(false),
                        ipv4Addresses = netIf.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    private enum class RegistrationPhase {
        REGISTERING,
        REGISTERED,
        UNREGISTERING,
        TERMINATED
    }

    private class RegistrationSession(
        val generation: Long,
        val onStatusChanged: (Boolean, String?) -> Unit,
        val unpublishLatch: CountDownLatch = CountDownLatch(1),
        var multicastLock: MulticastLockHandle? = null,
        var listener: NsdRegistrationHandle? = null,
        var phase: RegistrationPhase = RegistrationPhase.REGISTERING,
        var unpublishRequested: Boolean = false,
        var unregisterDispatched: Boolean = false
    )

    private val stateLock = Any()
    private var currentGeneration: Long = 0L
    private var activeSession: RegistrationSession? = null

    override fun resolveLocalLanIpAddress(): String {
        return runCatching {
            selectBestLanIpv4Address(interfaceSnapshotProvider())
        }.getOrDefault("127.0.0.1")
    }

    override fun observeNetworkChanges(onNetworkChanged: () -> Unit): AutoCloseable {
        return networkMonitor.observe(onNetworkChanged)
    }

    override fun publishMdnsService(
        serviceName: String,
        port: Int,
        fingerprintSha256: String,
        deviceId: String,
        onStatusChanged: (Boolean) -> Unit
    ) {
        publishMdnsService(
            serviceName = serviceName,
            port = port,
            fingerprintSha256 = fingerprintSha256,
            deviceId = deviceId,
            deviceName = null
        ) { isPublished, _ ->
            onStatusChanged(isPublished)
        }
    }

    override fun publishMdnsService(
        serviceName: String,
        port: Int,
        fingerprintSha256: String,
        deviceId: String,
        deviceName: String?,
        onStatusChanged: (isPublished: Boolean, resolvedServiceName: String?) -> Unit
    ) {
        if (port !in 1..65535) {
            telemetryRepository.recordLog(
                tag = "mDNS-ERR",
                message = "Puerto inválido para publicación mDNS: $port",
                severity = AuditSeverity.WARNING
            )
            onStatusChanged(false, null)
            return
        }

        if (!nsdBackend.isAvailable()) {
            telemetryRepository.recordLog(
                tag = "mDNS-WARN",
                message = "Servicio NsdManager no disponible en este dispositivo. Conexión directa IP+PIN disponible.",
                severity = AuditSeverity.WARNING
            )
            onStatusChanged(false, null)
            return
        }

        val myGen: Long
        val previousTeardown: TeardownAction?
        synchronized(stateLock) {
            myGen = ++currentGeneration
            val previous = activeSession
            activeSession = null
            previousTeardown = previous?.let { prepareTeardownLocked(it) }
        }

        if (previousTeardown != null) {
            executeTeardown(previousTeardown)
            if (previousTeardown.awaitLatch != null) {
                runCatching {
                    previousTeardown.awaitLatch.await(UNREGISTER_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }
            }
        }

        val session: RegistrationSession
        synchronized(stateLock) {
            if (myGen != currentGeneration) {
                return
            }
            session = RegistrationSession(
                generation = myGen,
                onStatusChanged = onStatusChanged
            )
            activeSession = session
        }

        val acquiredLock = runCatching { multicastController.acquire(MULTICAST_LOCK_TAG) }.getOrNull()
        if (acquiredLock == null) {
            telemetryRepository.recordLog(
                tag = "mDNS-WARN",
                message = "No se pudo adquirir WifiManager.MulticastLock (verifica permiso CHANGE_WIFI_MULTICAST_STATE y estado Wi-Fi).",
                severity = AuditSeverity.WARNING
            )
        }
        val cancelledBeforeRegister = synchronized(stateLock) {
            if (session.unpublishRequested || session.generation != currentGeneration || session.phase == RegistrationPhase.TERMINATED) {
                true
            } else {
                session.multicastLock = acquiredLock
                false
            }
        }
        if (cancelledBeforeRegister) {
            acquiredLock?.release()
            completeSessionAsTerminated(session)
            return
        }

        val normalizedServiceName = serviceName.trim().ifBlank { SERVICE_NAME_DEFAULT }
        val request = MdnsRegistrationRequest(
            serviceName = normalizedServiceName,
            serviceType = normalizeAndroidServiceType(SERVICE_TYPE),
            port = port,
            txtAttributes = buildTxtAttributes(
                fingerprintSha256 = fingerprintSha256,
                deviceId = deviceId,
                deviceName = deviceName
            )
        )

        val callbacks = createCallbacks(session = session, port = port)

        runCatching {
            nsdBackend.registerService(request, callbacks)
        }.onSuccess { handle ->
            var unregisterNow: NsdRegistrationHandle? = null
            synchronized(stateLock) {
                session.listener = handle
                if ((session.unpublishRequested || session.generation != currentGeneration) &&
                    session.phase != RegistrationPhase.TERMINATED &&
                    !session.unregisterDispatched
                ) {
                    session.phase = RegistrationPhase.UNREGISTERING
                    session.unregisterDispatched = true
                    unregisterNow = handle
                }
            }
            unregisterNow?.let { dispatchUnregister(session, it) }
        }.onFailure { e ->
            val notifyFailure = completeSessionAsTerminated(session)
            if (notifyFailure) {
                telemetryRepository.recordLog(
                    tag = "mDNS-ERR",
                    message = "Excepción al publicar servicio NSD: ${e.message}",
                    severity = AuditSeverity.WARNING
                )
                onStatusChanged(false, null)
            }
        }
    }

    override fun unpublishMdnsService() {
        val teardown: TeardownAction?
        synchronized(stateLock) {
            currentGeneration++
            val session = activeSession
            activeSession = null
            teardown = session?.let { prepareTeardownLocked(it) }
        }
        teardown?.let { executeTeardown(it) }
    }

    private data class TeardownAction(
        val session: RegistrationSession,
        val handleToUnregister: NsdRegistrationHandle?,
        val lockToRelease: MulticastLockHandle?,
        val awaitLatch: CountDownLatch?
    )

    private fun prepareTeardownLocked(session: RegistrationSession): TeardownAction {
        session.unpublishRequested = true
        val lockToRelease = session.multicastLock
        session.multicastLock = null

        val handle = session.listener
        val shouldUnregister = handle != null &&
            (session.phase == RegistrationPhase.REGISTERING || session.phase == RegistrationPhase.REGISTERED) &&
            !session.unregisterDispatched

        if (shouldUnregister) {
            session.phase = RegistrationPhase.UNREGISTERING
            session.unregisterDispatched = true
            return TeardownAction(
                session = session,
                handleToUnregister = handle,
                lockToRelease = lockToRelease,
                awaitLatch = session.unpublishLatch
            )
        }

        if (handle == null || session.phase == RegistrationPhase.TERMINATED) {
            session.phase = RegistrationPhase.TERMINATED
            session.unpublishLatch.countDown()
            return TeardownAction(
                session = session,
                handleToUnregister = null,
                lockToRelease = lockToRelease,
                awaitLatch = null
            )
        }

        return TeardownAction(
            session = session,
            handleToUnregister = null,
            lockToRelease = lockToRelease,
            awaitLatch = if (session.phase == RegistrationPhase.UNREGISTERING) session.unpublishLatch else null
        )
    }

    private fun executeTeardown(action: TeardownAction) {
        action.lockToRelease?.release()
        action.handleToUnregister?.let { handle ->
            dispatchUnregister(action.session, handle)
        }
    }

    private fun dispatchUnregister(session: RegistrationSession, handle: NsdRegistrationHandle) {
        runCatching {
            nsdBackend.unregisterService(handle)
        }.onFailure {
            completeSessionAsTerminated(session)
        }
    }

    private fun createCallbacks(
        session: RegistrationSession,
        port: Int
    ): NsdRegistrationCallbacks {
        return object : NsdRegistrationCallbacks {
            override fun onRegistered(resolvedServiceName: String) {
                var staleHandleToUnregister: NsdRegistrationHandle? = null
                var lockToReleaseOnStale: MulticastLockHandle? = null
                val shouldNotifyActive: Boolean

                synchronized(stateLock) {
                    val stale = session.unpublishRequested || session.generation != currentGeneration
                    if (stale) {
                        shouldNotifyActive = false
                        lockToReleaseOnStale = session.multicastLock
                        session.multicastLock = null
                        if (!session.unregisterDispatched && session.listener != null) {
                            session.phase = RegistrationPhase.UNREGISTERING
                            session.unregisterDispatched = true
                            staleHandleToUnregister = session.listener
                        }
                    } else {
                        shouldNotifyActive = true
                        session.phase = RegistrationPhase.REGISTERED
                    }
                }

                lockToReleaseOnStale?.release()
                staleHandleToUnregister?.let { dispatchUnregister(session, it) }

                if (shouldNotifyActive) {
                    val finalName = resolvedServiceName.ifBlank { SERVICE_NAME_DEFAULT }
                    telemetryRepository.recordLog(
                        tag = "mDNS-NSD",
                        message = "Servicio mDNS publicado: $finalName.$SERVICE_TYPE_FQDN (UDP $MDNS_STANDARD_PORT -> HTTPS:$port)",
                        severity = AuditSeverity.SUCCESS
                    )
                    session.onStatusChanged(true, finalName)
                }
            }

            override fun onRegistrationFailed(errorCode: Int) {
                val shouldNotify = completeSessionAsTerminated(session)
                if (shouldNotify) {
                    telemetryRepository.recordLog(
                        tag = "mDNS-WARN",
                        message = "No se pudo registrar mDNS (código $errorCode). Conexión directa IP+PIN disponible.",
                        severity = AuditSeverity.WARNING
                    )
                    session.onStatusChanged(false, null)
                }
            }

            override fun onUnregistered() {
                val shouldNotify = completeSessionAsTerminated(session)
                if (shouldNotify) {
                    session.onStatusChanged(false, null)
                }
            }

            override fun onUnregistrationFailed(errorCode: Int) {
                val shouldNotify = completeSessionAsTerminated(session)
                if (shouldNotify) {
                    session.onStatusChanged(false, null)
                }
            }
        }
    }

    private fun completeSessionAsTerminated(session: RegistrationSession): Boolean {
        val shouldNotify: Boolean
        val lockToRelease: MulticastLockHandle?
        synchronized(stateLock) {
            shouldNotify = (session.generation == currentGeneration && !session.unpublishRequested)
            session.phase = RegistrationPhase.TERMINATED
            if (activeSession === session) {
                activeSession = null
            }
            lockToRelease = session.multicastLock
            session.multicastLock = null
            session.unpublishLatch.countDown()
        }
        lockToRelease?.release()
        return shouldNotify
    }

    private class AndroidNsdBackend(context: Context) : NsdBackend {
        private val nsdManager: NsdManager? by lazy {
            context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        }

        private class Handle(val listener: NsdManager.RegistrationListener) : NsdRegistrationHandle

        override fun isAvailable(): Boolean = nsdManager != null

        override fun registerService(
            request: MdnsRegistrationRequest,
            callbacks: NsdRegistrationCallbacks
        ): NsdRegistrationHandle {
            val manager = nsdManager ?: throw IllegalStateException("NsdManager no disponible")
            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = request.serviceName
                this.serviceType = request.serviceType
                this.port = request.port
                request.txtAttributes.forEach { (key, value) ->
                    setAttribute(key, value)
                }
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(registeredInfo: NsdServiceInfo) {
                    callbacks.onRegistered(registeredInfo.serviceName.orEmpty())
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    callbacks.onRegistrationFailed(errorCode)
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    callbacks.onUnregistered()
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    callbacks.onUnregistrationFailed(errorCode)
                }
            }
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            return Handle(listener)
        }

        override fun unregisterService(handle: NsdRegistrationHandle) {
            val androidHandle = handle as? Handle ?: return
            nsdManager?.unregisterService(androidHandle.listener)
        }
    }

    private class AndroidMulticastLockController(context: Context) : MulticastLockController {
        private val wifiManager: WifiManager? by lazy {
            context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        }

        override fun acquire(tag: String): MulticastLockHandle? {
            val lock = runCatching {
                wifiManager?.createMulticastLock(tag)?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.getOrNull() ?: return null

            return object : MulticastLockHandle {
                private val released = AtomicBoolean(false)

                override fun release() {
                    if (released.compareAndSet(false, true)) {
                        runCatching {
                            if (lock.isHeld) {
                                lock.release()
                            }
                        }
                    }
                }
            }
        }
    }

    private class AndroidNetworkChangeMonitor(context: Context) : NetworkChangeMonitor {
        private val connectivityManager: ConnectivityManager? by lazy {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }

        override fun observe(onNetworkChanged: () -> Unit): AutoCloseable {
            val manager = connectivityManager ?: return AutoCloseable {}
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onNetworkChanged()
                }

                override fun onLost(network: Network) {
                    onNetworkChanged()
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    onNetworkChanged()
                }
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()

            val registered = runCatching {
                manager.registerNetworkCallback(request, callback)
                true
            }.getOrDefault(false)

            if (!registered) return AutoCloseable {}

            val closed = AtomicBoolean(false)
            return AutoCloseable {
                if (closed.compareAndSet(false, true)) {
                    runCatching { manager.unregisterNetworkCallback(callback) }
                }
            }
        }
    }
}
