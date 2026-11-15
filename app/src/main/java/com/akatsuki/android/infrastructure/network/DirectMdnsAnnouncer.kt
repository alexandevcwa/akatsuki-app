package com.akatsuki.android.infrastructure.network

import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.repository.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Emisor directo de paquetes mDNS Multicast por Socket UDP (RFC 6762 / RFC 6763).
 *
 * Complementa a `NsdManager` de Android emitiendo directamente paquetes binarios DNS-SD
 * sobre UDP 5353 hacia la IP Multicast 224.0.0.251 y Broadcast 255.255.255.255.
 */
class DirectMdnsAnnouncer(
    private val telemetryRepository: TelemetryRepository
) {
    private var announceJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startAnnouncing(
        serviceName: String,
        serviceType: String,
        port: Int,
        txtAttributes: Map<String, String>,
        localIpAddress: String
    ) {
        stopAnnouncing()
        isRunning.set(true)

        announceJob = scope.launch {
            val packetBytes = buildMdnsResponsePacket(
                instanceName = serviceName,
                serviceType = serviceType,
                port = port,
                txtAttributes = txtAttributes,
                localIpAddress = localIpAddress
            )

            if (packetBytes.isEmpty()) {
                telemetryRepository.recordLog(
                    tag = "mDNS-DIRECT",
                    message = "No se pudo construir paquete binario mDNS (IP inválida: $localIpAddress).",
                    severity = AuditSeverity.WARNING
                )
                return@launch
            }

            telemetryRepository.recordLog(
                tag = "mDNS-DIRECT",
                message = "Iniciando emisor directo UDP Multicast mDNS (Puerto 5353) para '$serviceName'.",
                severity = AuditSeverity.INFO
            )

            var announceCount = 0
            while (isActive && isRunning.get()) {
                val sentCount = broadcastMdnsPacket(packetBytes)
                announceCount++

                if (announceCount == 1 || announceCount % 5 == 0) {
                    telemetryRepository.recordLog(
                        tag = "mDNS-DIRECT",
                        message = "Paquete mDNS UDP 5353 transmitido vía socket a 224.0.0.251:5353 ($sentCount interfaces notificadas).",
                        severity = AuditSeverity.SUCCESS
                    )
                }
                delay(3500L.milliseconds)
            }
        }
    }

    fun stopAnnouncing() {
        isRunning.set(false)
        announceJob?.cancel()
        announceJob = null
    }

    private fun broadcastMdnsPacket(packetBytes: ByteArray): Int {
        var successCount = 0
        val multicastAddr = runCatching { InetAddress.getByName("224.0.0.251") }.getOrNull()
        val broadcastAddr = runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull()

        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true

                multicastAddr?.let { mAdder ->
                    val packet = DatagramPacket(packetBytes, packetBytes.size, mAdder, 5353)
                    runCatching { socket.send(packet); successCount++ }
                }

                broadcastAddr?.let { bAdder ->
                    val packet = DatagramPacket(packetBytes, packetBytes.size, bAdder, 5353)
                    runCatching { socket.send(packet); successCount++ }
                }
            }
        }

        // Envío explícito sobre interfaces de red activas (Wi-Fi, Hotspot, Ethernet)
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (netIf in interfaces) {
                if (!netIf.isUp || netIf.isLoopback) continue
                for (inetAdder in netIf.inetAddresses.toList()) {
                    if (inetAdder.isLoopbackAddress) continue
                    runCatching {
                        DatagramSocket(null).use { socket ->
                            socket.reuseAddress = true
                            socket.bind(java.net.InetSocketAddress(inetAdder, 0))
                            multicastAddr?.let { mAdder ->
                                socket.send(DatagramPacket(packetBytes, packetBytes.size, mAdder, 5353))
                                successCount++
                            }
                        }
                    }
                }
            }
        }

        return successCount
    }

    companion object {
        internal fun buildMdnsResponsePacket(
            instanceName: String,
            serviceType: String,
            port: Int,
            txtAttributes: Map<String, String>,
            localIpAddress: String
        ): ByteArray {
            val ipBytes = runCatching { InetAddress.getByName(localIpAddress).address }.getOrNull()
                ?: return byteArrayOf()
            if (ipBytes.size != 4) return byteArrayOf() // IPv4 por defecto

            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)

            // DNS Header
            dos.writeShort(0x0000) // Transaction ID
            dos.writeShort(0x8400) // Flags: Response, Authoritative
            dos.writeShort(0x0000) // Questions = 0
            dos.writeShort(0x0004) // Answers = 4 (PTR, SRV, TXT, A)
            dos.writeShort(0x0000) // Authority = 0
            dos.writeShort(0x0000) // Additional = 0

            val cleanServiceType = serviceType.trim().trimEnd('.')
            val ptrName = "$cleanServiceType.local"
            val fullServiceName = "$instanceName.$cleanServiceType.local"
            val hostName = "$instanceName.local"

            // Registro PTR (_akatsuki._tcp.local -> AkatsukiNode._akatsuki._tcp.local)
            writeDnsName(dos, ptrName)
            dos.writeShort(12) // Type PTR
            dos.writeShort(0x8001) // Class IN + Flush
            dos.writeInt(120) // TTL 120s
            val ptrData = encodeDnsName(fullServiceName)
            dos.writeShort(ptrData.size)
            dos.write(ptrData)

            // Registro SRV (AkatsukiNode._akatsuki._tcp.local -> Port 8443, Target AkatsukiNode.local)
            writeDnsName(dos, fullServiceName)
            dos.writeShort(33) // Type SRV
            dos.writeShort(0x8001) // Class IN + Flush
            dos.writeInt(120) // TTL 120s
            val targetNameData = encodeDnsName(hostName)
            dos.writeShort(6 + targetNameData.size)
            dos.writeShort(0) // Priority 0
            dos.writeShort(0) // Weight 0
            dos.writeShort(port) // Port (e.g. 8443)
            dos.write(targetNameData)

            // Registro TXT (AkatsukiNode._akatsuki._tcp.local -> fp=..., id=..., name=...)
            writeDnsName(dos, fullServiceName)
            dos.writeShort(16) // Type TXT
            dos.writeShort(0x8001) // Class IN + Flush
            dos.writeInt(120) // TTL 120s
            val txtBytes = encodeTxtAttributes(txtAttributes)
            dos.writeShort(txtBytes.size)
            dos.write(txtBytes)

            // Registro A (AkatsukiNode.local -> IP Address)
            writeDnsName(dos, hostName)
            dos.writeShort(1) // Type A
            dos.writeShort(0x8001) // Class IN + Flush
            dos.writeInt(120) // TTL 120s
            dos.writeShort(4) // Length 4
            dos.write(ipBytes)

            dos.flush()
            return baos.toByteArray()
        }

        private fun writeDnsName(dos: DataOutputStream, name: String) {
            val labels = name.split('.').filter { it.isNotEmpty() }
            for (label in labels) {
                val bytes = label.toByteArray(Charsets.UTF_8)
                dos.writeByte(bytes.size)
                dos.write(bytes)
            }
            dos.writeByte(0)
        }

        private fun encodeDnsName(name: String): ByteArray {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            writeDnsName(dos, name)
            dos.flush()
            return baos.toByteArray()
        }

        private fun encodeTxtAttributes(attributes: Map<String, String>): ByteArray {
            if (attributes.isEmpty()) return byteArrayOf(0)
            val baos = ByteArrayOutputStream()
            for ((key, value) in attributes) {
                val str = "$key=$value"
                val bytes = str.toByteArray(Charsets.UTF_8)
                val len = bytes.size.coerceAtMost(255)
                baos.write(len)
                baos.write(bytes, 0, len)
            }
            return baos.toByteArray()
        }
    }
}
