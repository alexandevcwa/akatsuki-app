package com.akatsuki.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akatsuki.android.core.designsystem.theme.AkatsukiCyberpunkTheme
import com.akatsuki.android.infrastructure.service.AkatsukiServerForegroundService
import com.akatsuki.android.presentation.dashboard.DashboardScreen
import com.akatsuki.android.presentation.dashboard.DashboardViewModel
import com.akatsuki.android.presentation.dashboard.HudIntent
import com.akatsuki.android.presentation.splash.AkatsukiSplashScreen

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels {
        val app = application as AkatsukiApplication
        DashboardViewModel.provideFactory(
            container = app.appContainer,
            onStartForegroundServer = {
                requestNotificationPermissionIfNeeded()
                AkatsukiServerForegroundService.startService(this)
            },
            onStopForegroundServer = {
                AkatsukiServerForegroundService.stopService(this)
            }
        )
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val storageSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onIntent(HudIntent.RefreshNetworkAndPermissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AkatsukiCyberpunkTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(durationMillis = 450),
                    label = "splashToDashboardTransition"
                ) { isSplashVisible ->
                    if (isSplashVisible) {
                        AkatsukiSplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    } else {
                        DashboardScreen(
                            state = uiState,
                            onIntent = viewModel::onIntent,
                            onRequestStoragePermission = ::openAllFilesAccessSettings
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onIntent(HudIntent.RefreshNetworkAndPermissions)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun openAllFilesAccessSettings() {
        val intent = runCatching {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
        }.getOrElse {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
        runCatching {
            storageSettingsLauncher.launch(intent)
        }.onFailure {
            storageSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
