package com.safemode.llconnect.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.AttachmentMerge
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.queue.QueuedAttachment
import com.safemode.llconnect.data.queue.QueuedFields
import com.safemode.llconnect.data.remote.models.EquipmentRecordRequest
import com.safemode.llconnect.data.remote.models.ExtraField
import com.safemode.llconnect.data.remote.models.FileAttachment
import com.safemode.llconnect.data.remote.models.GasRecordRequest
import com.safemode.llconnect.data.remote.models.GenericRecordRequest
import com.safemode.llconnect.data.remote.models.NoteRequest
import com.safemode.llconnect.data.remote.models.OdometerRecordRequest
import com.safemode.llconnect.data.remote.models.PlanRecordRequest
import com.safemode.llconnect.data.remote.models.ReminderRecordRequest
import com.safemode.llconnect.data.remote.models.SupplyRecordRequest
import com.safemode.llconnect.data.remote.models.TaxRecordRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Runs the record form's server work: saving, deleting, and uploading a scanned receipt.
 *
 * The form's fields stay in the composable (rememberSaveable); this only owns the work in flight,
 * so it keeps going when the screen is recreated by a rotation or theme change. In the
 * composition's own coroutine scope it was cancelled part way, and a save that had already reached
 * the server would then be retried by the user and created twice.
 *
 * One piece of work runs at a time. Results come back as [Outcome] events, which the current
 * composition applies to its fields.
 */
class RecordFormViewModel : ViewModel() {

    enum class Work { SAVING, DELETING, UPLOADING_SCAN }

    sealed interface Outcome {
        /** The record was saved, queued ([queued]), or deleted: the form should close. */
        data class Closed(val queued: Boolean = false) : Outcome

        data class Failed(val message: String) : Outcome

        /** A scan was uploaded and should be attached to the record. */
        data class ScanUploaded(val attachment: FileAttachment) : Outcome

        /** A scan couldn't be uploaded now and was staged on the device to go up later. */
        data class ScanStaged(val attachment: QueuedAttachment) : Outcome

        /** A staged scan was uploaded while saving an edit; it moves from pending to attached. */
        data class StagedUploaded(val staged: QueuedAttachment, val attachment: FileAttachment) : Outcome
    }

    private val _work = MutableStateFlow<Work?>(null)
    val work: StateFlow<Work?> = _work.asStateFlow()

    // Unbounded so an outcome that lands while the screen is being recreated waits for the new one.
    private val _outcomes = Channel<Outcome>(Channel.UNLIMITED)
    val outcomes: Flow<Outcome> = _outcomes.receiveAsFlow()

    private fun emit(outcome: Outcome) {
        _outcomes.trySend(outcome)
    }

    private fun launchWork(kind: Work, block: suspend () -> Unit) {
        if (_work.value != null) return
        _work.value = kind
        viewModelScope.launch {
            try {
                block()
            } finally {
                _work.value = null
            }
        }
    }

    /** Uploads the scan at [path] as [name], or stages it when the server can't take it now. */
    fun uploadScan(path: String, name: String, mimeType: String) = launchWork(Work.UPLOADING_SCAN) {
        val file = File(path)
        if (!file.exists()) {
            emit(Outcome.Failed("Couldn't read the scanned image."))
            return@launchWork
        }
        try {
            // The file is streamed or moved, never read into memory, so a full-quality scan can't
            // exhaust memory here.
            if (Graph.serverStatus.isReachable()) {
                Graph.repository.uploadDocument(file, name, mimeType).fold(
                    onSuccess = {
                        emit(Outcome.ScanUploaded(it))
                        runCatching { file.delete() }
                    },
                    onFailure = {
                        // Likely just lost the server - stage it locally so it uploads with the
                        // record once it flushes from the queue.
                        emit(Outcome.ScanStaged(Graph.offlineQueue.stageImageFile(file, name, mimeType)))
                    },
                )
            } else {
                emit(Outcome.ScanStaged(Graph.offlineQueue.stageImageFile(file, name, mimeType)))
            }
        } catch (e: IOException) {
            emit(Outcome.Failed("Couldn't keep the scanned image: ${e.message}"))
        }
    }

    /**
     * Saves an edit. Edits are online-only, so receipts staged because their upload failed go up
     * first. The record is then re-fetched right before saving: the update replaces the whole
     * record, so the form's loaded snapshot would wipe anything changed elsewhere in the meantime.
     * Attachments are merged: the server's current list, plus scans added here, minus files
     * removed here ([loadedAttachments] is the list as the form first loaded it).
     */
    fun saveEdit(
        area: RecordArea,
        vehicleId: String,
        recordId: String,
        values: FormValues,
        loadedAttachments: List<FileAttachment>,
        pending: List<QueuedAttachment>,
    ) = launchWork(Work.SAVING) {
        var files = values.files
        for (staged in pending) {
            val file = Graph.offlineQueue.stagedFile(staged)
            val uploaded = if (file != null && file.exists()) {
                Graph.repository.uploadDocument(file, staged.fileName, staged.mimeType)
            } else {
                Result.failure(IllegalStateException("The scanned image is missing."))
            }
            uploaded.fold(
                onSuccess = {
                    files = files + it
                    emit(Outcome.StagedUploaded(staged, it))
                    Graph.offlineQueue.discardStaged(staged)
                },
                onFailure = {
                    emit(Outcome.Failed("Couldn't upload the scanned receipt: ${it.message}"))
                    return@launchWork
                },
            )
        }

        val latest = Graph.repository.getRecordForEdit(area, vehicleId, recordId).getOrNull()
        val latestFiles = latest?.files?.let { AttachmentMerge.merge(loadedAttachments, files, it) } ?: files
        val latestExtra = latest?.extraFields ?: values.extraFields
        submit(
            area = area,
            vehicleId = vehicleId,
            id = recordId.toLongOrNull(),
            values = values.copy(files = latestFiles, extraFields = latestExtra),
        ).fold(
            onSuccess = { emit(Outcome.Closed()) },
            onFailure = { emit(Outcome.Failed(it.message ?: "Couldn't save the record.")) },
        )
    }

    /**
     * Saves a new record: live when the server is reachable and every receipt is already
     * uploaded, otherwise to the offline queue (which flushes right away when the server is
     * reachable, so an online save with pending receipts still goes up now).
     */
    fun saveNew(
        area: RecordArea,
        vehicleId: String,
        vehicleName: String,
        fields: QueuedFields,
        attachments: List<FileAttachment>,
        pending: List<QueuedAttachment>,
    ) = launchWork(Work.SAVING) {
        suspend fun queue() {
            val uploaded = attachments.map {
                QueuedAttachment(it.name ?: "attachment", "image/jpeg", location = it.location)
            }
            try {
                Graph.offlineQueue.enqueue(
                    area = area,
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    fields = fields,
                    uploaded = uploaded,
                    staged = pending,
                )
                emit(Outcome.Closed(queued = true))
            } catch (e: IOException) {
                // Stay on the form with the input intact.
                emit(Outcome.Failed("Couldn't save this entry on the device: ${e.message}"))
            }
        }

        if (Graph.serverStatus.isReachable() && pending.isEmpty()) {
            Graph.repository.add(area, vehicleId, fields, attachments).fold(
                onSuccess = { emit(Outcome.Closed()) },
                onFailure = { e ->
                    if (!Graph.serverStatus.isReachable()) {
                        queue()
                    } else {
                        emit(Outcome.Failed(e.message ?: "Couldn't save the record."))
                    }
                },
            )
        } else {
            queue()
        }
    }

    fun delete(area: RecordArea, recordId: String) = launchWork(Work.DELETING) {
        Graph.repository.deleteRecord(area, recordId).fold(
            onSuccess = { emit(Outcome.Closed()) },
            onFailure = { emit(Outcome.Failed(it.message ?: "Delete failed.")) },
        )
    }
}

/** All editable form fields, bundled so [submit] stays readable. */
data class FormValues(
    val date: String,
    val odometer: Long?,
    val initialOdometer: Long?,
    val description: String?,
    val cost: Double?,
    val fuelConsumed: Double?,
    val isFillToFull: Boolean,
    val missedFuelUp: Boolean,
    val notes: String?,
    val tags: String?,
    val planType: String,
    val priority: String,
    val progress: String,
    val partNumber: String?,
    val partSupplier: String?,
    val partQuantity: Long?,
    val dueDate: String,
    val dueOdometer: Long?,
    val metric: String,
    val isEquipped: Boolean,
    val noteText: String?,
    val pinned: Boolean,
    val files: List<FileAttachment>,
    val extraFields: List<ExtraField>?,
)

private suspend fun submit(
    area: RecordArea,
    vehicleId: String,
    id: Long?,
    values: FormValues,
): Result<Unit> {
    val repo = Graph.repository
    val isEdit = id != null
    return when (area) {
        RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE -> {
            val body = GenericRecordRequest(
                id = id,
                date = values.date,
                odometer = values.odometer,
                description = values.description,
                cost = values.cost,
                notes = values.notes,
                tags = values.tags,
                files = values.files.ifEmpty { null },
                extraFields = values.extraFields,
            )
            if (isEdit) repo.updateServiceLike(area, body) else repo.addServiceLike(area, vehicleId, body)
        }
        RecordArea.TAX -> {
            val body = TaxRecordRequest(
                id = id,
                date = values.date,
                description = values.description,
                cost = values.cost,
                notes = values.notes,
                tags = values.tags,
                files = values.files.ifEmpty { null },
                extraFields = values.extraFields,
            )
            if (isEdit) repo.updateTax(body) else repo.addTax(vehicleId, body)
        }
        RecordArea.GAS -> {
            val body = GasRecordRequest(
                id = id,
                date = values.date,
                odometer = values.odometer,
                fuelConsumed = values.fuelConsumed,
                cost = values.cost,
                isFillToFull = values.isFillToFull,
                missedFuelUp = values.missedFuelUp,
                notes = values.notes,
                tags = values.tags,
                files = values.files.ifEmpty { null },
                extraFields = values.extraFields,
            )
            if (isEdit) repo.updateGas(body) else repo.addGas(vehicleId, body)
        }
        RecordArea.ODOMETER -> {
            val body = OdometerRecordRequest(
                id = id,
                date = values.date,
                // The update endpoint rejects an empty initial odometer; echo the loaded
                // value back (defaulting to 0 if absent). On add, leave it null so the
                // server derives it as before.
                initialOdometer = if (isEdit) (values.initialOdometer ?: 0L) else values.initialOdometer,
                odometer = values.odometer,
                notes = values.notes,
                tags = values.tags,
                files = values.files.ifEmpty { null },
                extraFields = values.extraFields,
            )
            if (isEdit) repo.updateOdometer(body) else repo.addOdometer(vehicleId, body)
        }
        RecordArea.PLAN -> {
            val body = PlanRecordRequest(
                id = id,
                description = values.description,
                cost = values.cost,
                type = values.planType,
                priority = values.priority,
                progress = values.progress,
                notes = values.notes,
                files = values.files.ifEmpty { null },
                extraFields = values.extraFields,
            )
            if (isEdit) repo.updatePlan(body) else repo.addPlan(vehicleId, body)
        }
        RecordArea.SUPPLY -> {
            val body = SupplyRecordRequest(
                id = id,
                date = values.date,
                partNumber = values.partNumber,
                partSupplier = values.partSupplier,
                partQuantity = values.partQuantity,
                description = values.description,
                cost = values.cost,
                notes = values.notes,
                tags = values.tags,
                files = values.files.ifEmpty { null },
                extraFields = values.extraFields,
            )
            if (isEdit) repo.updateSupply(body) else repo.addSupply(vehicleId, body)
        }
        RecordArea.REMINDER -> {
            val body = ReminderRecordRequest(
                id = id,
                description = values.description,
                dueDate = if (values.metric != "Odometer") values.dueDate else null,
                dueOdometer = if (values.metric != "Date") values.dueOdometer else null,
                metric = values.metric,
                notes = values.notes,
                tags = values.tags,
            )
            if (isEdit) repo.updateReminder(body) else repo.addReminder(vehicleId, body)
        }
        RecordArea.EQUIPMENT -> {
            val body = EquipmentRecordRequest(
                id = id,
                description = values.description,
                isEquipped = values.isEquipped,
                notes = values.notes,
                tags = values.tags,
                files = values.files.ifEmpty { null },
                extraFields = values.extraFields,
            )
            if (isEdit) repo.updateEquipment(body) else repo.addEquipment(vehicleId, body)
        }
        RecordArea.NOTE -> {
            val body = NoteRequest(
                id = id,
                description = values.description,
                noteText = values.noteText,
                pinned = values.pinned,
                tags = values.tags,
                files = values.files.ifEmpty { null },
                extraFields = values.extraFields,
            )
            if (isEdit) repo.updateNote(body) else repo.addNote(vehicleId, body)
        }
    }
}
