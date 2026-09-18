package com.safemode.llconnect.data

import com.safemode.llconnect.data.remote.models.ExtraField
import com.safemode.llconnect.data.remote.models.FileAttachment

/**
 * A unified, editable view of a record used to pre-fill the record form when updating.
 * Only the fields the form can edit are captured; irrelevant fields per area stay null.
 *
 * [files] and [extraFields] aren't editable in the form but MUST be sent back on update:
 * LubeLogger replaces the whole record, so omitting them wipes attachments and custom fields.
 */
data class RecordEditData(
    val id: Long,
    val date: String? = null,
    val odometer: Long? = null,
    // Odometer records carry a separate initial reading; the update endpoint requires it.
    val initialOdometer: Long? = null,
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
    // Preserved-through-edit (not user-editable in the form)
    val files: List<FileAttachment> = emptyList(),
    val extraFields: List<ExtraField>? = null,
)
