package com.akatsuki.android

import com.akatsuki.android.data.repository.InMemoryTelemetryRepository
import com.akatsuki.android.infrastructure.network.DirectMdnsAnnouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectMdnsAnnouncerTest {

    @Test
    fun `construye paquete binario DNS-SD mDNS valido para IPv4`() {
        val bytes = DirectMdnsAnnouncer.buildMdnsResponsePacket(
            instanceName = "AkatsukiNode",
            serviceType = "_akatsuki._tcp",
            port = 8443,
            txtAttributes = mapOf("fp" to "abc123sha", "id" to "node-01"),
            localIpAddress = "192.168.1.50"
        )

        assertTrue("El paquete DNS-SD no debe estar vacío", bytes.isNotEmpty())
        assertTrue("El paquete debe tener al menos 60 bytes", bytes.size > 60)

        // Verificar Transaction ID = 0x0000
        assertEquals(0, bytes[0].toInt())
        assertEquals(0, bytes[1].toInt())

        // Verificar Flags = 0x8400 (Response, Authoritative)
        assertEquals(0x84.toByte(), bytes[2])
        assertEquals(0x00.toByte(), bytes[3])

        // Verificar ANCOUNT = 4 (PTR, SRV, TXT, A)
        assertEquals(0x00.toByte(), bytes[6])
        assertEquals(0x04.toByte(), bytes[7])
    }

    @Test
    fun `retorna paquete vacio si la IP es invalida`() {
        val bytes = DirectMdnsAnnouncer.buildMdnsResponsePacket(
            instanceName = "AkatsukiNode",
            serviceType = "_akatsuki._tcp",
            port = 8443,
            txtAttributes = emptyMap(),
            localIpAddress = "invalid-ip-address"
        )

        assertEquals(0, bytes.size)
    }

    @Test
    fun `inicia y detiene el emisor directo sin excepciones`() {
        val telemetry = InMemoryTelemetryRepository()
        val announcer = DirectMdnsAnnouncer(telemetry)

        announcer.startAnnouncing(
            serviceName = "AkatsukiNode",
            serviceType = "_akatsuki._tcp",
            port = 8443,
            txtAttributes = mapOf("fp" to "test-fp"),
            localIpAddress = "127.0.0.1"
        )

        Thread.sleep(100)
        announcer.stopAnnouncing()

        assertTrue(telemetry.auditLogs.value.any { it.tag == "MDNS-DIRECT" })
    }
}
