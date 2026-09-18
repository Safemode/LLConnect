package com.safemode.llconnect.data

import com.safemode.llconnect.data.remote.models.EquipmentRecord
import com.safemode.llconnect.data.remote.models.EquipmentRecordRequest
import com.safemode.llconnect.data.remote.models.FileAttachment
import com.safemode.llconnect.data.remote.models.GasRecord
import com.safemode.llconnect.data.remote.models.GasRecordRequest
import com.safemode.llconnect.data.remote.models.GenericRecord
import com.safemode.llconnect.data.remote.models.GenericRecordRequest
import com.safemode.llconnect.data.remote.models.Note
import com.safemode.llconnect.data.remote.models.NoteRequest
import com.safemode.llconnect.data.remote.models.OdometerRecord
import com.safemode.llconnect.data.remote.models.OdometerRecordRequest
import com.safemode.llconnect.data.remote.models.PlanRecord
import com.safemode.llconnect.data.remote.models.PlanRecordRequest
import com.safemode.llconnect.data.remote.models.ReminderRecord
import com.safemode.llconnect.data.remote.models.SupplyRecord
import com.safemode.llconnect.data.remote.models.SupplyRecordRequest
import com.safemode.llconnect.data.remote.models.TaxRecordRequest

private fun money(value: Double?): String? =
    value?.let { "$" + String.format("%,.2f", it) }

private fun odo(value: Long?): String? =
    value?.let { String.format("%,d", it) + " mi/km" }

internal fun GenericRecord.toRow(isTax: Boolean = false): RecordRow = RecordRow(
    id = id?.toString() ?: "",
    title = description?.ifBlank { "(no description)" } ?: "(no description)",
    subtitle = notes?.ifBlank { null },
    trailing = money(cost),
    meta = listOfNotNull(date, if (!isTax) odo(odometer) else null).joinToString(" · ")
        .ifBlank { null },
)

internal fun GasRecord.toRow(): RecordRow = RecordRow(
    id = id?.toString() ?: "",
    title = buildString {
        append(fuelConsumed?.let { String.format("%,.2f units", it) } ?: "Fuel-up")
        if (isFillToFull == true) append(" · full")
    },
    subtitle = notes?.ifBlank { null }
        ?: fuelEconomy?.let { String.format("%.1f economy", it) },
    trailing = money(cost),
    meta = listOfNotNull(date, odo(odometer)).joinToString(" · ").ifBlank { null },
)

internal fun OdometerRecord.toRow(): RecordRow = RecordRow(
    id = id?.toString() ?: "",
    title = odo(odometer) ?: "Odometer reading",
    subtitle = notes?.ifBlank { null },
    trailing = null,
    meta = date,
)

internal fun PlanRecord.toRow(): RecordRow = RecordRow(
    id = id?.toString() ?: "",
    title = description?.ifBlank { "(no description)" } ?: "(no description)",
    subtitle = listOfNotNull(type, progress, priority?.let { "priority: $it" })
        .joinToString(" · ").ifBlank { null },
    trailing = money(cost),
    meta = dateModified ?: dateCreated,
)

internal fun SupplyRecord.toRow(): RecordRow = RecordRow(
    id = id?.toString() ?: "",
    title = description?.ifBlank { partNumber } ?: partNumber ?: "(supply)",
    subtitle = listOfNotNull(
        partSupplier,
        partQuantity?.let { "qty $it" },
    ).joinToString(" · ").ifBlank { null },
    trailing = money(cost),
    meta = date,
)

internal fun ReminderRecord.toRow(): RecordRow = RecordRow(
    id = id?.toString() ?: "",
    title = description?.ifBlank { "(reminder)" } ?: "(reminder)",
    subtitle = listOfNotNull(
        urgency,
        metric,
    ).joinToString(" · ").ifBlank { null },
    trailing = urgency,
    meta = listOfNotNull(
        dueDate?.let { "due $it" },
        dueOdometer?.let { "at ${odo(it)}" },
    ).joinToString(" · ").ifBlank { null },
)

internal fun EquipmentRecord.toRow(): RecordRow = RecordRow(
    id = id?.toString() ?: "",
    title = description?.ifBlank { "(equipment)" } ?: "(equipment)",
    subtitle = notes?.ifBlank { null },
    trailing = if (isEquipped == true) "Equipped" else "Removed",
    meta = tags?.ifBlank { null },
)

// ---- Cross-vehicle activity mappers ----

internal fun GenericRecord.toActivity(area: RecordArea, name: (Long?) -> String): ActivityItem =
    ActivityItem(
        area = area,
        vehicleId = vehicleId,
        vehicleName = name(vehicleId),
        title = description?.ifBlank { "(no description)" } ?: "(no description)",
        date = date,
        cost = cost,
    )

internal fun GasRecord.toActivity(name: (Long?) -> String): ActivityItem = ActivityItem(
    area = RecordArea.GAS,
    vehicleId = vehicleId,
    vehicleName = name(vehicleId),
    title = fuelConsumed?.let { String.format("%,.2f units", it) } ?: "Fuel-up",
    date = date,
    cost = cost,
)

internal fun OdometerRecord.toActivity(name: (Long?) -> String): ActivityItem = ActivityItem(
    area = RecordArea.ODOMETER,
    vehicleId = vehicleId,
    vehicleName = name(vehicleId),
    title = odo(odometer) ?: "Odometer reading",
    date = date,
    cost = null,
)

// ---- Editable-form mappers (used when updating a record) ----

internal fun GenericRecord.toEdit(): RecordEditData = RecordEditData(
    id = id ?: 0L,
    date = date,
    odometer = odometer,
    description = description,
    cost = cost,
    notes = notes,
    tags = tags,
)

internal fun GasRecord.toEdit(): RecordEditData = RecordEditData(
    id = id ?: 0L,
    date = date,
    odometer = odometer,
    cost = cost,
    fuelConsumed = fuelConsumed,
    isFillToFull = isFillToFull,
    missedFuelUp = missedFuelUp,
    notes = notes,
    tags = tags,
)

internal fun OdometerRecord.toEdit(): RecordEditData = RecordEditData(
    id = id ?: 0L,
    date = date,
    odometer = odometer,
    initialOdometer = initialOdometer,
    notes = notes,
    tags = tags,
)

internal fun PlanRecord.toEdit(): RecordEditData = RecordEditData(
    id = id ?: 0L,
    description = description,
    cost = cost,
    type = type,
    priority = priority,
    progress = progress,
    notes = notes,
)

internal fun SupplyRecord.toEdit(): RecordEditData = RecordEditData(
    id = id ?: 0L,
    date = date,
    partNumber = partNumber,
    partSupplier = partSupplier,
    partQuantity = partQuantity,
    description = description,
    cost = cost,
    notes = notes,
    tags = tags,
)

internal fun ReminderRecord.toEdit(): RecordEditData = RecordEditData(
    id = id ?: 0L,
    description = description,
    dueDate = dueDate,
    dueOdometer = dueOdometer,
    metric = metric,
    notes = notes,
    tags = tags,
)

internal fun EquipmentRecord.toEdit(): RecordEditData = RecordEditData(
    id = id ?: 0L,
    description = description,
    isEquipped = isEquipped,
    notes = notes,
    tags = tags,
)

internal fun Note.toEdit(): RecordEditData = RecordEditData(
    id = id ?: 0L,
    description = description,
    noteText = noteText,
    pinned = pinned,
    tags = tags,
)

// ---- Response → update-request mappers, preserving all fields while replacing `files`.
// Used by the attachments manager so editing files doesn't wipe other data. ----

internal fun GenericRecord.toUpdateRequest(files: List<FileAttachment>): GenericRecordRequest =
    GenericRecordRequest(
        id = id, date = date, odometer = odometer, description = description, cost = cost,
        notes = notes, tags = tags, extraFields = extraFields, files = files,
    )

internal fun GenericRecord.toTaxUpdateRequest(files: List<FileAttachment>): TaxRecordRequest =
    TaxRecordRequest(
        id = id, date = date, description = description, cost = cost,
        notes = notes, tags = tags, extraFields = extraFields, files = files,
    )

internal fun GasRecord.toUpdateRequest(files: List<FileAttachment>): GasRecordRequest =
    GasRecordRequest(
        id = id, date = date, odometer = odometer, fuelConsumed = fuelConsumed, cost = cost,
        isFillToFull = isFillToFull, missedFuelUp = missedFuelUp, notes = notes, tags = tags,
        extraFields = extraFields, files = files, startingSoc = startingSoc, endingSoc = endingSoc,
    )

internal fun OdometerRecord.toUpdateRequest(files: List<FileAttachment>): OdometerRecordRequest =
    OdometerRecordRequest(
        id = id, date = date, initialOdometer = initialOdometer, odometer = odometer,
        notes = notes, tags = tags, extraFields = extraFields, files = files,
    )

internal fun PlanRecord.toUpdateRequest(files: List<FileAttachment>): PlanRecordRequest =
    PlanRecordRequest(
        id = id, description = description, cost = cost, type = type, priority = priority,
        progress = progress, notes = notes, extraFields = extraFields, files = files,
    )

internal fun SupplyRecord.toUpdateRequest(files: List<FileAttachment>): SupplyRecordRequest =
    SupplyRecordRequest(
        id = id, date = date, partNumber = partNumber, partSupplier = partSupplier,
        partQuantity = partQuantity, description = description, cost = cost, notes = notes,
        tags = tags, extraFields = extraFields, files = files,
    )

internal fun EquipmentRecord.toUpdateRequest(files: List<FileAttachment>): EquipmentRecordRequest =
    EquipmentRecordRequest(
        id = id, description = description, isEquipped = isEquipped, notes = notes, tags = tags,
        extraFields = extraFields, files = files,
    )

internal fun Note.toUpdateRequest(files: List<FileAttachment>): NoteRequest =
    NoteRequest(
        id = id, description = description, noteText = noteText, pinned = pinned, tags = tags,
        extraFields = extraFields, files = files,
    )

internal fun Note.toRow(): RecordRow = RecordRow(
    id = id?.toString() ?: "",
    title = description?.ifBlank { "(note)" } ?: "(note)",
    subtitle = noteText?.ifBlank { null },
    trailing = if (pinned == true) "📌" else null,
    meta = tags?.ifBlank { null },
)
