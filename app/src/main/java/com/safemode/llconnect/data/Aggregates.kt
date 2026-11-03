package com.safemode.llconnect.data

/** A single cross-vehicle activity entry for the History feed. */
data class ActivityItem(
    val area: RecordArea,
    val vehicleId: Long?,
    val vehicleName: String,
    val title: String,
    val date: String?,
    val cost: Double?,
)

/**
 * The History feed. [unavailable] lists record types whose endpoint failed (for example one an
 * older server doesn't have), so the screen can say what's missing instead of failing outright.
 */
data class ActivityFeed(
    val items: List<ActivityItem>,
    val unavailable: List<RecordArea> = emptyList(),
)

/** Aggregated spend for the Reports screen. [unavailable] is as in [ActivityFeed]. */
data class CostReport(
    val totalCost: Double,
    val recordCount: Int,
    val byCategory: List<CategoryTotal>,
    val byVehicle: List<VehicleTotal>,
    val unavailable: List<RecordArea> = emptyList(),
)

data class CategoryTotal(val area: RecordArea, val total: Double, val count: Int)

data class VehicleTotal(val vehicleName: String, val total: Double, val count: Int)

/** A cross-vehicle reminder for the Reminders screen. */
data class ReminderItem(
    val vehicleName: String,
    val description: String,
    val dueDate: String?,
    val dueOdometer: Long?,
    val urgency: String?,
    val metric: String?,
)
