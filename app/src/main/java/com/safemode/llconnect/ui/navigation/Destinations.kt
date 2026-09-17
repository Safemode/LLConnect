package com.safemode.llconnect.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DASHBOARD = "dashboard"
    const val VEHICLES = "vehicles"
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
    SERVER(Routes.SERVER, "Server", Icons.Filled.Dns),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}
