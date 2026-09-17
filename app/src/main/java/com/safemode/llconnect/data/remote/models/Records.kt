package com.safemode.llconnect.data.remote.models

import com.safemode.llconnect.data.remote.FlexDouble
import com.safemode.llconnect.data.remote.FlexLong

/**
 * Service / Repair / Upgrade / Tax records share this response shape.
 * (Tax records omit `odometer`.)
 */
data class GenericRecord(
    @FlexLong val id: Long? = null,
    @FlexLong val vehicleId: Long? = null,
    val date: String? = null,
    @FlexLong val odometer: Long? = null,
    val description: String? = null,
    val notes: String? = null,
    @FlexDouble val cost: Double? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachmentResponse>? = null,
)

/** Body for adding a Service/Repair/Upgrade record. */
data class GenericRecordRequest(
    val id: Long? = null,
    val date: String? = null,
    val odometer: Long? = null,
    val description: String? = null,
    val cost: Double? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachment>? = null,
)

/** Body for adding a Tax record (no odometer). */
data class TaxRecordRequest(
    val id: Long? = null,
    val date: String? = null,
    val description: String? = null,
    val cost: Double? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachment>? = null,
)

/** GET /api/vehicle/odometerrecords */
data class OdometerRecord(
    @FlexLong val id: Long? = null,
    @FlexLong val vehicleId: Long? = null,
    val date: String? = null,
    @FlexLong val initialOdometer: Long? = null,
    @FlexLong val odometer: Long? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachmentResponse>? = null,
)

data class OdometerRecordRequest(
    val id: Long? = null,
    val date: String? = null,
    val initialOdometer: Long? = null,
    val odometer: Long? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachment>? = null,
    val equipmentRecordId: String? = null,
)

/** GET /api/vehicle/gasrecords */
data class GasRecord(
    @FlexLong val id: Long? = null,
    @FlexLong val vehicleId: Long? = null,
    val date: String? = null,
    @FlexLong val odometer: Long? = null,
    @FlexDouble val fuelConsumed: Double? = null,
    @FlexDouble val cost: Double? = null,
    val isFillToFull: Boolean? = null,
    val missedFuelUp: Boolean? = null,
    @FlexDouble val fuelEconomy: Double? = null,
    val notes: String? = null,
    val tags: String? = null,
    @FlexLong val startingSoc: Long? = null,
    @FlexLong val endingSoc: Long? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachmentResponse>? = null,
)

data class GasRecordRequest(
    val id: Long? = null,
    val date: String? = null,
    val odometer: Long? = null,
    val fuelConsumed: Double? = null,
    val cost: Double? = null,
    val isFillToFull: Boolean? = null,
    val missedFuelUp: Boolean? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachment>? = null,
    val startingSoc: Long? = null,
    val endingSoc: Long? = null,
)

/** GET /api/vehicle/planrecords */
data class PlanRecord(
    @FlexLong val id: Long? = null,
    @FlexLong val vehicleId: Long? = null,
    val dateCreated: String? = null,
    val dateModified: String? = null,
    val description: String? = null,
    @FlexDouble val cost: Double? = null,
    val type: String? = null,
    val priority: String? = null,
    val progress: String? = null,
    val notes: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachmentResponse>? = null,
)

data class PlanRecordRequest(
    val id: Long? = null,
    val description: String? = null,
    val cost: Double? = null,
    val type: String? = null,
    val priority: String? = null,
    val progress: String? = null,
    val notes: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachment>? = null,
)

/** GET /api/vehicle/supplyrecords */
data class SupplyRecord(
    @FlexLong val id: Long? = null,
    @FlexLong val vehicleId: Long? = null,
    val date: String? = null,
    val partNumber: String? = null,
    val partSupplier: String? = null,
    @FlexLong val partQuantity: Long? = null,
    val description: String? = null,
    @FlexDouble val cost: Double? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachmentResponse>? = null,
)

data class SupplyRecordRequest(
    val id: Long? = null,
    val date: String? = null,
    val partNumber: String? = null,
    val partSupplier: String? = null,
    val partQuantity: Long? = null,
    val description: String? = null,
    val cost: Double? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachment>? = null,
)

/** GET /api/vehicle/reminders */
data class ReminderRecord(
    @FlexLong val id: Long? = null,
    @FlexLong val vehicleId: Long? = null,
    val description: String? = null,
    val dueDate: String? = null,
    @FlexLong val dueOdometer: Long? = null,
    val metric: String? = null,
    val urgency: String? = null,
    val notes: String? = null,
    val tags: String? = null,
)

data class ReminderRecordRequest(
    val id: Long? = null,
    val description: String? = null,
    val dueDate: String? = null,
    val dueOdometer: Long? = null,
    val metric: String? = null,
    val notes: String? = null,
    val tags: String? = null,
)

/** GET /api/vehicle/equipmentrecords */
data class EquipmentRecord(
    @FlexLong val id: Long? = null,
    @FlexLong val vehicleId: Long? = null,
    val description: String? = null,
    val isEquipped: Boolean? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachmentResponse>? = null,
)

data class EquipmentRecordRequest(
    val id: Long? = null,
    val description: String? = null,
    val isEquipped: Boolean? = null,
    val notes: String? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachment>? = null,
)

/** GET /api/vehicle/notes */
data class Note(
    @FlexLong val id: Long? = null,
    @FlexLong val vehicleId: Long? = null,
    val description: String? = null,
    val noteText: String? = null,
    val pinned: Boolean? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachmentResponse>? = null,
)

data class NoteRequest(
    val id: Long? = null,
    val description: String? = null,
    val noteText: String? = null,
    val pinned: Boolean? = null,
    val tags: String? = null,
    val extraFields: List<ExtraField>? = null,
    val files: List<FileAttachment>? = null,
)
