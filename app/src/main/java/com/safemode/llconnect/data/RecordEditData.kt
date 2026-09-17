package com.safemode.llconnect.data

/**
 * A unified, editable view of a record used to pre-fill the record form when updating.
 * Only the fields the form can edit are captured; irrelevant fields per area stay null.
 */
data class RecordEditData(
    val id: Long,
    val date: String? = null,
    val odometer: Long? = null,
    val description: String? = null,
    val cost: Double? = null,
    val fuelConsumed: Double? = null,
    val isFillToFull: Boolean? = null,
    val missedFuelUp: Boolean? = null,
    val notes: String? = null,
    val tags: String? = null,
    // Planner
    val type: String? = null,
    val priority: String? = null,
    val progress: String? = null,
    // Supplies
    val partNumber: String? = null,
    val partSupplier: String? = null,
    val partQuantity: Long? = null,
    // Reminders
    val dueDate: String? = null,
    val dueOdometer: Long? = null,
    val metric: String? = null,
    // Equipment
    val isEquipped: Boolean? = null,
    // Notes
    val noteText: String? = null,
    val pinned: Boolean? = null,
)
