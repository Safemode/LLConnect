package com.safemode.llconnect.data.remote.models

import com.safemode.llconnect.data.remote.FlexDouble
import com.safemode.llconnect.data.remote.FlexLong

/** GET /api/vehicles and /api/vehicle/info */
data class Vehicle(
    @FlexLong val id: Long? = null,
    val imageLocation: String? = null,
    val mapLocation: String? = null,
    val year: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val licensePlate: String? = null,
    val purchaseDate: String? = null,
    val soldDate: String? = null,
    @FlexDouble val purchasePrice: Double? = null,
    @FlexDouble val soldPrice: Double? = null,
    val isElectric: Boolean? = null,
    val isDiesel: Boolean? = null,
    val useHours: Boolean? = null,
    val odometerOptional: Boolean? = null,
    val extraFields: List<ExtraField>? = null,
    val tags: List<String>? = null,
    val hasOdometerAdjustment: Boolean? = null,
    val odometerMultiplier: String? = null,
    val odometerDifference: String? = null,
    val dashboardMetrics: List<String>? = null,
    val vehicleIdentifier: String? = null,
) {
    val displayName: String
        get() = listOfNotNull(year?.takeIf { it > 0 }?.toString(), make, model)
            .joinToString(" ")
            .ifBlank { "Vehicle #$id" }
}

/** Body for POST /api/vehicles/add */
data class VehicleAddRequest(
    val year: Long? = null,
    val make: String? = null,
    val model: String? = null,
    val identifier: String? = null,
    val licensePlate: String? = null,
    val fuelType: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
)

/** Body for PUT /api/vehicles/update */
data class VehicleUpdateRequest(
    val id: Long,
    val year: Long? = null,
    val make: String? = null,
    val model: String? = null,
    val identifier: String? = null,
    val licensePlate: String? = null,
    val fuelType: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
)
