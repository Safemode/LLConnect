package com.safemode.llconnect

import android.content.Context
import com.safemode.llconnect.data.LubeLoggerRepository
import com.safemode.llconnect.data.remote.ApiProvider
import com.safemode.llconnect.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Manual dependency container. Initialized once from [LLConnectApp]. */
object Graph {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var apiProvider: ApiProvider
        private set

    lateinit var repository: LubeLoggerRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        settingsRepository = SettingsRepository(context.applicationContext)
        apiProvider = ApiProvider()
        repository = LubeLoggerRepository(apiProvider)

        // Keep the API provider's config in sync with saved settings.
        settingsRepository.config
            .onEach { apiProvider.updateConfig(it) }
            .launchIn(appScope)
    }
}
