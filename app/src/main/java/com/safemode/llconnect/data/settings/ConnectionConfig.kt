package com.safemode.llconnect.data.settings

enum class AuthMode { API_KEY, BASIC }

/** How fuel economy is reported by the gas endpoints. */
enum class FuelEconomyUnit(val label: String) {
    DEFAULT("Server default"),
    US_MPG("US MPG"),
    UK_MPG("UK MPG"),
}

/** Order for chronological record lists (odometer, service, gas, …). */
enum class RecordSortOrder(val label: String) {
    OLDEST_FIRST("Oldest first"),
    NEWEST_FIRST("Newest first"),
}

/** Selectable on-device HTTP cache size limits. */
enum class CacheSize(val label: String, val bytes: Long) {
    MB_25("25 MB", 25L * 1024 * 1024),
    MB_50("50 MB", 50L * 1024 * 1024),
    MB_100("100 MB", 100L * 1024 * 1024),
    MB_250("250 MB", 250L * 1024 * 1024),
    MB_500("500 MB", 500L * 1024 * 1024),
}

/** User-supplied connection details for a self-hosted LubeLogger instance. */
data class ConnectionConfig(
    val scheme: String = "http",
    val host: String = "",
    val port: String = "",
    val apiKey: String = "",
    val authMode: AuthMode = AuthMode.API_KEY,
    val basicUsername: String = "",
    val basicPassword: String = "",
    val cultureInvariant: Boolean = true,
    val fuelEconomyUnit: FuelEconomyUnit = FuelEconomyUnit.DEFAULT,
    val recordSortOrder: RecordSortOrder = RecordSortOrder.OLDEST_FIRST,
    val cacheSize: CacheSize = CacheSize.MB_50,
) {
    /** True once there is enough to attempt a connection. */
    val isConfigured: Boolean
        get() = host.isNotBlank() && when (authMode) {
            AuthMode.API_KEY -> apiKey.isNotBlank()
            AuthMode.BASIC -> basicUsername.isNotBlank()
        }

    /** Normalized base URL with trailing slash, e.g. https://10.0.0.5:8080/ */
    fun baseUrl(): String {
        var h = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val portPart = port.trim().takeIf { it.isNotBlank() }?.let { ":$it" } ?: ""
        // If the host already contains a port, don't append another.
        if (h.substringAfterLast(':', "").toIntOrNull() != null && portPart.isNotEmpty()) {
            h = h.substringBeforeLast(':')
        }
        return "$scheme://$h$portPart/"
    }
}
