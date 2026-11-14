package com.akatsuki.android.infrastructure.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.akatsuki.android.AkatsukiApplication
import com.akatsuki.android.MainActivity
import com.akatsuki.android.R
import com.akatsuki.android.domain.model.AuditSeverity
import com.akatsuki.android.domain.model.ServerLifecycleState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Servicio en Primer Plano (ForegroundService) según la Parte 4.2 de la especificación:
 * - Mantiene activo el servidor Ktor HTTPS y la publicación mDNS durante transferencias grandes
 *   incluso con la pantalla apagada.
 * - Muestra una notificación persistente estilo Akatsuki con la IP local, el PIN activo
 *   y botones para Rotar PIN o Apagar el Web Service.
 */
class AkatsukiServerForegroundService : LifecycleService() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "akatsuki_https_node_channel"
        private const val NOTIFICATION_ID = 8443

        const val ACTION_START_SERVER = "com.akatsuki.android.action.START_SERVER"
        const val ACTION_STOP_SERVER = "com.akatsuki.android.action.STOP_SERVER"
        const val ACTION_ROTATE_PIN = "com.akatsuki.android.action.ROTATE_PIN"

        fun startService(context: Context) {
            val intent = Intent(context, AkatsukiServerForegroundService::class.java).apply {
                action = ACTION_START_SERVER
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AkatsukiServerForegroundService::class.java).apply {
                action = ACTION_STOP_SERVER
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as AkatsukiApplication).appContainer

        // Observar cambios de estado del servidor, PIN y sesiones para actualizar la notificación en vivo
        lifecycleScope.launch {
            combine(
                container.telemetryRepository.serverState,
                container.sessionAuthRepository.pinState,
                container.sessionAuthRepository.activeSessions
            ) { serverState, pinState, sessions ->
                Triple(serverState, pinState, sessions.size)
            }.collect { (serverState, pinState, sessionCount) ->
                if (serverState is ServerLifecycleState.Running) {
                    val notification = buildHudNotification(
                        endpoint = "https://${serverState.ipAddress}:${serverState.port}",
                        pinCode = pinState.pinCode,
                        activeSessionsCount = sessionCount
                    )
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val container = (application as AkatsukiApplication).appContainer

        when (intent?.action) {
            ACTION_STOP_SERVER -> {
                lifecycleScope.launch {
                    container.ktorServer.stopServer()
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_ROTATE_PIN -> {
                val newPin = container.sessionAuthRepository.rotatePin()
                container.telemetryRepository.recordLog(
                    tag = "PIN-ROTATE",
                    message = "PIN efímero regenerado manualmente (${newPin.pinCode}).",
                    severity = AuditSeverity.INFO
                )
            }
            else -> {
                val initialPin = container.sessionAuthRepository.pinState.value.pinCode
                val ip = container.networkDiscoveryGateway.resolveLocalLanIpAddress()
                val port = container.nodeConfigRepository.portFlow.value
                val initialNotification = buildHudNotification(
                    endpoint = "https://$ip:$port",
                    pinCode = initialPin,
                    activeSessionsCount = container.sessionAuthRepository.activeSessions.value.size
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        initialNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotification)
                }

                acquireWakeLock()
                lifecycleScope.launch {
                    container.ktorServer.startServer()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        val container = (application as AkatsukiApplication).appContainer
        container.applicationScope.launch {
            container.ktorServer.stopServer()
        }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AkatsukiNode::HttpsTransferWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // Hasta 6 horas continuas
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun buildHudNotification(
        endpoint: String,
        pinCode: String,
        activeSessionsCount: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rotatePinIntent = Intent(this, AkatsukiServerForegroundService::class.java).apply {
            action = ACTION_ROTATE_PIN
        }
        val rotatePendingIntent = PendingIntent.getService(
            this,
            101,
            rotatePinIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AkatsukiServerForegroundService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = "Endpoint: $endpoint • PIN: $pinCode • Sesiones: $activeSessionsCount"

        val largeLogo = runCatching {
            BitmapFactory.decodeResource(resources, R.drawable.akatsuki_logo)
        }.getOrNull()

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setLargeIcon(largeLogo)
            .setContentTitle("AKATSUKI // HTTPS WEB SERVICE ONLINE")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFE11D48.toInt())
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_popup_sync,
                "ROTAR PIN",
                rotatePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "APAGAR WEB SERVICE",
                stopPendingIntent
            )
            .build()
    }
}
