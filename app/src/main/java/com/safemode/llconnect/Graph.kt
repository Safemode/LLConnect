package com.safemode.llconnect

import android.content.Context
import com.safemode.llconnect.data.LubeLoggerRepository
import com.safemode.llconnect.data.remote.ApiProvider
import com.safemode.llconnect.data.remote.NetworkMonitor
import com.safemode.llconnect.data.remote.ServerStatus
import com.safemode.llconnect.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Manual dependency container. Initialized once from [LLConnectApp]. */
object Graph {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var apiProvider: ApiProvider
        private set

    lateinit var repository: LubeLoggerRepository
        private set

    lateinit var serverStatus: ServerStatus
        private set

    private lateinit var networkMonitor: NetworkMonitor

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        val app = context.applicationContext
        settingsRepository = SettingsRepository(app)
        serverStatus = ServerStatus()
        networkMonitor = NetworkMonitor(app, serverStatus)
        apiProvider = ApiProvider(app.cacheDir, serverStatus, networkMonitor::isOnline)
        repository = LubeLoggerRepository(apiProvider)

        // Keep the API provider's config in sync with saved settings.
        settingsRepository.config
            .onEach { apiProvider.updateConfig(it) }
            .launchIn(appScope)

        // Background reachability check: whenever a network is present but the server isn't
        // confirmed reachable (at launch, or after it went down), quietly probe it off the UI
        // thread. This resolves the "checking" state into reachable/unreachable and reconnects
        // on its own — the UI never waits on it and keeps serving cached data meanwhile.
        appScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (networkMonitor.isOnline() && !serverStatus.isReachable()) {
                    serverStatus.report(apiProvider.probeReachable())
                }
                delay(if (serverStatus.isReachable()) 15_000 else 5_000)
            }
        }
    }
}
