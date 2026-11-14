package com.akatsuki.android.data.repository

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akatsuki.android.domain.repository.NodeConfigRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val Context.akatsukiDataStore by preferencesDataStore(name = "akatsuki_node_config")

class NodeConfigDataStoreRepository(
    private val context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : NodeConfigRepository {

    companion object {
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        private val KEY_SERVER_PORT = intPreferencesKey("server_port")
        const val DEFAULT_PORT = 8443
    }

    private val initialized = CompletableDeferred<Unit>()

    private val _deviceIdFlow = MutableStateFlow("ak-node-android-01")
    override val deviceIdFlow: StateFlow<String> = _deviceIdFlow.asStateFlow()

    private val _deviceNameFlow = MutableStateFlow(buildDefaultDeviceName())
    override val deviceNameFlow: StateFlow<String> = _deviceNameFlow.asStateFlow()

    private val _portFlow = MutableStateFlow(DEFAULT_PORT)
    override val portFlow: StateFlow<Int> = _portFlow.asStateFlow()

    init {
        scope.launch {
            try {
                val prefs = context.akatsukiDataStore.data.first()
                val storedId = prefs[KEY_DEVICE_ID]
                val storedName = prefs[KEY_DEVICE_NAME]
                val storedPort = prefs[KEY_SERVER_PORT]

                val finalId = storedId ?: "ak-node-android-${UUID.randomUUID().toString().take(6)}"
                val finalName = storedName ?: buildDefaultDeviceName()
                val finalPort = storedPort ?: DEFAULT_PORT

                _deviceIdFlow.value = finalId
                _deviceNameFlow.value = finalName
                _portFlow.value = finalPort

                if (storedId == null || storedName == null || storedPort == null) {
                    context.akatsukiDataStore.edit { mutable ->
                        mutable[KEY_DEVICE_ID] = finalId
                        mutable[KEY_DEVICE_NAME] = finalName
                        mutable[KEY_SERVER_PORT] = finalPort
                    }
                }
            } finally {
                initialized.complete(Unit)
            }
        }
    }

    override suspend fun awaitInitialized() {
        initialized.await()
    }

    override suspend fun updateDeviceName(newName: String) {
        initialized.await()
        val sanitized = newName.trim().ifEmpty { buildDefaultDeviceName() }
        _deviceNameFlow.value = sanitized
        context.akatsukiDataStore.edit { prefs ->
            prefs[KEY_DEVICE_NAME] = sanitized
        }
    }

    override suspend fun updatePort(newPort: Int) {
        initialized.await()
        val validPort = newPort.coerceIn(1024, 65535)
        _portFlow.value = validPort
        context.akatsukiDataStore.edit { prefs ->
            prefs[KEY_SERVER_PORT] = validPort
        }
    }

    private fun buildDefaultDeviceName(): String {
        val model = Build.MODEL.uppercase(Locale.US).ifBlank { "ANDROID NODE" }
        return "$model // ITACHI"
    }
}
