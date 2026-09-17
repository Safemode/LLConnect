package com.safemode.llconnect.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DASHBOARD = "dashboard"
    const val VEHICLES = "vehicles"
    const val REMINDERS = "reminders"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val TOOLS = "tools"
    const val ABOUT = "about"
    const val SERVER = "server"
    const val SETTINGS = "settings"

    fun vehicleDetail(vehicleId: String) = "vehicle/$vehicleId"
    const val VEHICLE_DETAIL = "vehicle/{vehicleId}"

    fun records(vehicleId: String, area: String) = "records/$vehicleId/$area"
    const val RECORDS = "records/{vehicleId}/{area}"

    /** Record add/edit form. Pass [recordId] to edit an existing record. */
    fun recordForm(vehicleId: String, area: String, recordId: String? = null) =
        "recordForm/$vehicleId/$area?recordId=${recordId ?: ""}"
    const val RECORD_FORM = "recordForm/{vehicleId}/{area}?recordId={recordId}"

    fun attachments(vehicleId: String, area: String, recordId: String) =
        "attachments/$vehicleId/$area/$recordId"
    const val ATTACHMENTS = "attachments/{vehicleId}/{area}/{recordId}"

    /** Vehicle add/edit form. Pass [vehicleId] to edit an existing vehicle. */
    fun vehicleForm(vehicleId: String? = null) = "vehicleForm?vehicleId=${vehicleId ?: ""}"
    const val VEHICLE_FORM = "vehicleForm?vehicleId={vehicleId}"
}

/** Top-level destinations shown in the navigation drawer. */
enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD(Routes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
    VEHICLES(Routes.VEHICLES, "Vehicles", Icons.Filled.DirectionsCar),
    REMINDERS(Routes.REMINDERS, "Reminders", Icons.Filled.NotificationsActive),
    HISTORY(Routes.HISTORY, "History", Icons.Filled.History),
    REPORTS(Routes.REPORTS, "Reports", Icons.Filled.Assessment),
    TOOLS(Routes.TOOLS, "Tools", Icons.Filled.Construction),
    SERVER(Routes.SERVER, "Server", Icons.Filled.Dns),
    ABOUT(Routes.ABOUT, "About", Icons.Filled.Info),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}
