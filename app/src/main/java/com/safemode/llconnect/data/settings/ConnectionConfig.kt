package com.safemode.llconnect.data.settings

import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.scan.ReceiptQuality

enum class AuthMode { API_KEY, BASIC }

/** How fuel economy is reported by the gas endpoints. */
enum class FuelEconomyUnit(val label: String) {
    DEFAULT("Server default"),
    US_MPG("US MPG"),
    UK_MPG("UK MPG"),
}

/**
 * Distance unit used to label odometer readings. LubeLogger's API returns odometer values
 * as unitless numbers, so this is purely a display choice applied app-side.
 */
enum class DistanceUnit(val label: String, val suffix: String) {
    MILES("Miles", "mi"),
    KILOMETERS("Kilometers", "km"),
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

/** App appearance. Midnight is a dark theme with pure-black surfaces for OLED screens. */
enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    MIDNIGHT("Midnight"),
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
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
    val recordSortOrder: RecordSortOrder = RecordSortOrder.OLDEST_FIRST,
    val cacheSize: CacheSize = CacheSize.MB_50,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    /** The one vehicle (by id) marked as favorite, or blank if none. Drives dashboard quick entry. */
    val favoriteVehicleId: String = "",
    /** Post a daily local reminder to log an odometer reading. */
    val notifyOdometerDaily: Boolean = false,
    /** Time of day for the daily odometer reminder (and the daily notification check), as "HH:mm". */
    val odometerReminderTime: String = "18:00",
    /** Notify about reminders the server marks urgent/very-urgent or past-due. */
    val notifyReminders: Boolean = false,
    /** Notify when queued records finish uploading (or fail) during the daily check. */
    val notifyQueue: Boolean = false,
    /**
     * Record areas (by [RecordArea.name]) whose scanned receipts use full-resolution lossless PNG.
     * Any area not listed uses the default compressed JPEG. Empty by default.
     */
    val fullQualityAreas: Set<String> = emptySet(),
    /** Detect and crop the receipt automatically in the scanner (toggled from the camera screen). */
    val autoDetectDocuments: Boolean = true,
) {
    /** True when any notification type is enabled (drives scheduling the daily worker). */
    val anyNotificationEnabled: Boolean
        get() = notifyOdometerDaily || notifyReminders || notifyQueue

    /** The scan quality configured for [area] (compressed unless the area is opted into full quality). */
    fun receiptQuality(area: RecordArea): ReceiptQuality =
        if (area.name in fullQualityAreas) ReceiptQuality.FULL else ReceiptQuality.COMPRESSED
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
