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
)
