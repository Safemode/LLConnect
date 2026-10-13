package com.safemode.llconnect

import android.content.Context
import com.safemode.llconnect.data.LubeLoggerRepository
import com.safemode.llconnect.data.notifications.NotificationScheduler
import com.safemode.llconnect.data.notifications.Notifications
import com.safemode.llconnect.data.queue.OfflineQueue
import com.safemode.llconnect.data.remote.ApiProvider
import com.safemode.llconnect.data.remote.NetworkMonitor
import com.safemode.llconnect.data.remote.ServerStatus
import com.safemode.llconnect.data.remote.ServerReachability
import com.safemode.llconnect.data.settings.SettingsRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    lateinit var offlineQueue: OfflineQueue
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
        offlineQueue = OfflineQueue(app.filesDir, serverStatus, repository, appScope)

        // Keep the API provider's config in sync with saved settings.
        settingsRepository.config
            .onEach { apiProvider.updateConfig(it) }
            .launchIn(appScope)

        // Reachability check: whenever a network is present but the server isn't confirmed
        // reachable (at launch, or after it went down), quietly probe it off the UI thread. This
        // resolves the "checking" state into reachable/unreachable and reconnects on its own — the
        // UI never waits on it and keeps serving cached data meanwhile.
        //
        // The probe only exists to drive the offline banner, which nobody sees while the app is
        // backgrounded, so it's gated to the foreground via ProcessLifecycleOwner: repeatOnLifecycle
        // runs the loop while the process is STARTED and cancels it when the app leaves the
        // foreground. This stops the app from firing network requests every few seconds in the
        // background — the worst case being a LAN server that's away/off while Wi-Fi stays up.
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runReachabilityLoop()
            }
        }

        // Flush the offline queue whenever the server becomes reachable (at launch, or after it
        // comes back). Gated to the foreground like the probe, so it never runs network work in the
        // background. flush() guards itself against re-entrancy, so a status flip caused by its own
        // calls can't start a second flush.
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // StateFlow already de-duplicates, so collect directly; flush() no-ops unless the
                // server is reachable and the queue is non-empty. (Result is ignored here; only the
                // daily worker notifies about queue outcomes.)
                serverStatus.state.collect { state ->
                    if (state == ServerReachability.REACHABLE) offlineQueue.flush()
                }
            }
        }

        // Notifications: create channels and (re)schedule the daily worker from saved preferences.
        Notifications.ensureChannels(app)
        appScope.launch {
            NotificationScheduler.schedule(app, settingsRepository.config.first())
        }
    }

    /**
     * While in the foreground: probe whenever online and not confirmed reachable, backing off
     * geometrically between failed probes (so a persistently-down server isn't hammered). Once
     * reachable, suspend until the status leaves REACHABLE (a network change flips it back to
     * CHECKING) instead of waking on a fixed timer — so a steady connection costs nothing.
     */
    private suspend fun runReachabilityLoop() = withContext(Dispatchers.IO) {
        var backoffMs = PROBE_MIN_BACKOFF_MS
        while (isActive) {
            if (networkMonitor.isOnline() && !serverStatus.isReachable()) {
                serverStatus.report(apiProvider.probeReachable())
            }
            if (serverStatus.isReachable()) {
                backoffMs = PROBE_MIN_BACKOFF_MS
                serverStatus.state.first { it != ServerReachability.REACHABLE }
            } else {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(PROBE_MAX_BACKOFF_MS)
            }
        }
    }

    private const val PROBE_MIN_BACKOFF_MS = 5_000L
    private const val PROBE_MAX_BACKOFF_MS = 60_000L
}
