package com.akatsuki.android.data.repository

import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.model.SecurityAuditLog
import com.akatsuki.android.domain.model.ServerLifecycleState
import com.akatsuki.android.domain.model.TransferTelemetryItem
import com.akatsuki.android.domain.repository.TelemetryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InMemoryTelemetryRepository : TelemetryRepository {
    private val _serverState = MutableStateFlow<ServerLifecycleState>(ServerLifecycleState.Stopped)
    override val serverState: StateFlow<ServerLifecycleState> = _serverState.asStateFlow()

    private val _transfers = MutableStateFlow<List<TransferTelemetryItem>>(emptyList())
    override val transfers: StateFlow<List<TransferTelemetryItem>> = _transfers.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<SecurityAuditLog>>(
        listOf(
            SecurityAuditLog(
                id = UUID.randomUUID().toString(),
                timestamp = formatTime(),
                tag = "SYSTEM",
                message = "Nodo Akatsuki inicializado. Listo para levantar túnel TLS 1.3.",
                severity = AuditSeverity.INFO
            )
        )
    )
    override val auditLogs: StateFlow<List<SecurityAuditLog>> = _auditLogs.asStateFlow()

    override fun updateServerState(state: ServerLifecycleState) {
        _serverState.value = state
    }

    override fun upsertTransfer(item: TransferTelemetryItem) {
        _transfers.update { current ->
            val index = current.indexOfFirst { it.id == item.id }
            val updated = if (index >= 0) {
                current.toMutableList().apply { set(index, item) }
            } else {
                listOf(item) + current
            }
            updated.take(30)
        }
    }

    override fun recordLog(tag: String, message: String, severity: AuditSeverity) {
        val entry = SecurityAuditLog(
            id = UUID.randomUUID().toString(),
            timestamp = formatTime(),
            tag = tag.uppercase(Locale.US),
            message = message,
            severity = severity
        )
        _auditLogs.update { current ->
            (listOf(entry) + current).take(80)
        }
    }

    override fun clearLogs() {
        _auditLogs.value = emptyList()
    }

    private fun formatTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }
}
