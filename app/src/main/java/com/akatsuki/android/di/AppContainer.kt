package com.akatsuki.android.di

import android.content.Context
import com.akatsuki.android.data.repository.InMemoryTelemetryRepository
import com.akatsuki.android.data.repository.NodeConfigDataStoreRepository
import com.akatsuki.android.domain.repository.FileSystemGateway
import com.akatsuki.android.domain.repository.NetworkDiscoveryGateway
import com.akatsuki.android.domain.repository.NodeConfigRepository
import com.akatsuki.android.domain.repository.SessionAuthRepository
import com.akatsuki.android.domain.repository.TelemetryRepository
import com.akatsuki.android.domain.repository.TlsCertificateRepository
import com.akatsuki.android.domain.usecase.FileOperationsUseCases
import com.akatsuki.android.domain.usecase.ManagePinAndPairingUseCase
import com.akatsuki.android.infrastructure.crypto.SessionTokenManager
import com.akatsuki.android.infrastructure.crypto.TlsCertificateManager
import com.akatsuki.android.infrastructure.http.AkatsukiKtorServer
import com.akatsuki.android.infrastructure.network.AndroidNsdPublisher
import com.akatsuki.android.infrastructure.storage.AndroidFileSystemGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Contenedor de Inyección de Dependencias (Manual DI / Service Locator modular)
 * Diseñado bajo inversión de dependencias para desacoplar Presentation, Domain, Data e Infrastructure.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    val telemetryRepository: TelemetryRepository by lazy {
        InMemoryTelemetryRepository()
    }

    val nodeConfigRepository: NodeConfigRepository by lazy {
        NodeConfigDataStoreRepository(appContext)
    }

    val certificateRepository: TlsCertificateRepository by lazy {
        TlsCertificateManager(keyStoreDir = File(appContext.filesDir, "keystore"))
    }

    val sessionAuthRepository: SessionAuthRepository by lazy {
        SessionTokenManager()
    }

    val fileSystemGateway: FileSystemGateway by lazy {
        AndroidFileSystemGateway()
    }

    val networkDiscoveryGateway: NetworkDiscoveryGateway by lazy {
        AndroidNsdPublisher(
            context = appContext,
            telemetryRepository = telemetryRepository
        )
    }

    val managePinAndPairingUseCase: ManagePinAndPairingUseCase by lazy {
        ManagePinAndPairingUseCase(
            sessionAuthRepository = sessionAuthRepository,
            certificateRepository = certificateRepository,
            fileSystemGateway = fileSystemGateway,
            networkDiscoveryGateway = networkDiscoveryGateway,
            nodeConfigRepository = nodeConfigRepository,
            telemetryRepository = telemetryRepository
        )
    }

    val fileOperationsUseCases: FileOperationsUseCases by lazy {
        FileOperationsUseCases(
            fileSystemGateway = fileSystemGateway,
            telemetryRepository = telemetryRepository
        )
    }

    val ktorServer: AkatsukiKtorServer by lazy {
        AkatsukiKtorServer(
            nodeConfigRepository = nodeConfigRepository,
            certificateRepository = certificateRepository,
            sessionAuthRepository = sessionAuthRepository,
            networkDiscoveryGateway = networkDiscoveryGateway,
            telemetryRepository = telemetryRepository,
            managePinAndPairingUseCase = managePinAndPairingUseCase,
            fileOperationsUseCases = fileOperationsUseCases
        )
    }
}
