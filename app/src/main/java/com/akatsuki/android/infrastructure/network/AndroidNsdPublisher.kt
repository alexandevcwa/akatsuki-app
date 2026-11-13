package com.akatsuki.android.infrastructure.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.repository.NetworkDiscoveryGateway
import com.akatsuki.android.domain.repository.TelemetryRepository
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Publicación de Servicio mDNS/NSD y Resolución de IP LAN (Parte 2 y Parte 4.4):
 * - Registra un NsdServiceInfo con:
 *   - serviceName = "AkatsukiNode"
 *   - serviceType = "_akatsuki._tcp."
 *   - port = 8443
 *   - atributo TXT: setAttribute("fp", certFingerprintSha256)
 * - Mantiene un WifiManager.MulticastLock activo mientras el servidor está publicado.
 */
class AndroidNsdPublisher(
    private val context: Context,
    private val telemetryRepository: TelemetryRepository
) : NetworkDiscoveryGateway {

    companion object {
        const val SERVICE_NAME_DEFAULT = "AkatsukiNode"
        const val SERVICE_TYPE = "_akatsuki._tcp."
    }

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val wifiManager: WifiManager? by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    override fun resolveLocalLanIpAddress(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            // Priorizar interfaces Wi-Fi / Hotspot / Tether (wlan0, ap0, swlan0, rndis0, eth0)
            val prioritized = interfaces
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { netIf ->
                    val name = netIf.name.lowercase()
                    when {
                        name.startsWith("wlan") -> 0
                        name.startsWith("ap") || name.startsWith("swlan") -> 1
                        name.startsWith("rndis") || name.startsWith("usb") -> 2
                        name.startsWith("eth") -> 3
                        else -> 4
                    }
                }

            for (netIf in prioritized) {
                val addresses = netIf.inetAddresses.toList()
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("169.254.")) {
                            return@runCatching host
                        }
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    @Synchronized
    override fun publishMdnsService(
        serviceName: String,
        port: Int,
        fingerprintSha256: String,
        deviceId: String,
        onStatusChanged: (Boolean) -> Unit
    ) {
        unpublishMdnsService()

        runCatching {
            val lock = wifiManager?.createMulticastLock("akatsuki_mdns_lock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = lock

            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = serviceName.ifBlank { SERVICE_NAME_DEFAULT }
                this.serviceType = SERVICE_TYPE
                this.port = port
                setAttribute("fp", fingerprintSha256)
                setAttribute("id", deviceId)
            }

            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(registeredInfo: NsdServiceInfo) {
                    telemetryRepository.recordLog(
                        tag = "mDNS-NSD",
                        message = "Servicio publicado: ${registeredInfo.serviceName}.$SERVICE_TYPE (Puerto $port)",
                        severity = AuditSeverity.SUCCESS
                    )
                    onStatusChanged(true)
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    telemetryRepository.recordLog(
                        tag = "mDNS-WARN",
                        message = "No se pudo registrar mDNS (código $errorCode). Conexión directa IP+PIN disponible.",
                        severity = AuditSeverity.WARNING
                    )
                    onStatusChanged(false)
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    onStatusChanged(false)
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    onStatusChanged(false)
                }
            }

            registrationListener = listener
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { e ->
            telemetryRepository.recordLog(
                tag = "mDNS-ERR",
                message = "Excepción al publicar servicio NSD: ${e.message}",
                severity = AuditSeverity.WARNING
            )
            onStatusChanged(false)
        }
    }

    @Synchronized
    override fun unpublishMdnsService() {
        registrationListener?.let { listener ->
            runCatching { nsdManager?.unregisterService(listener) }
        }
        registrationListener = null

        multicastLock?.let { lock ->
            runCatching {
                if (lock.isHeld) {
                    lock.release()
                }
            }
        }
        multicastLock = null
    }
}
