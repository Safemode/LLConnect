package com.safemode.llconnect.data.queue

import com.safemode.llconnect.data.RecordArea

/**
 * A new record the user saved while the server was unavailable, held on-device until it can be
 * submitted. Scoped to adds only — edits/deletes stay online-only.
 *
 * @property id stable UUID, also the name of this entry's on-disk attachment directory.
 * @property createdAt epoch millis the entry was queued (for ordering + "queued Xm ago").
 * @property vehicleName snapshot of the vehicle's display name, so the queue list reads well even
 *   when the server (and its vehicle list) is unreachable.
 * @property uploadedAttachments attachments already stored on the server — only possible when a
 *   save started online, uploaded some receipts, then failed on the record write. Carried by
 *   reference and re-sent as-is at flush time.
 * @property pendingAttachments local images awaiting upload; uploaded during [flush] before the
 *   record is written.
 * @property lastError the most recent flush failure reason, shown in the queue; null if untried.
 */
data class QueuedEntry(
    val id: String,
    val createdAt: Long,
    val vehicleId: String,
    val vehicleName: String,
    val area: RecordArea,
    val fields: QueuedFields,
    val uploadedAttachments: List<QueuedAttachment> = emptyList(),
    val pendingAttachments: List<QueuedAttachment> = emptyList(),
    val lastError: String? = null,
)

/**
 * The add-relevant form values, mirroring the subset of the record form that applies to new
 * records. Nullable where the form leaves a field blank; strings are kept verbatim (notes/tags are
 * sent even when empty, matching what the live form does). `extraFields` is intentionally absent —
 * new records never carry it.
 */
data class QueuedFields(
    val date: String,
    val odometer: Long? = null,
    val initialOdometer: Long? = null,
    val description: String? = null,
    val cost: Double? = null,
    val fuelConsumed: Double? = null,
    val isFillToFull: Boolean = true,
    val missedFuelUp: Boolean = false,
    val notes: String = "",
    val tags: String = "",
    val planType: String = "ServiceRecord",
    val priority: String = "Normal",
    val progress: String = "Backlog",
    val partNumber: String? = null,
    val partSupplier: String? = null,
    val partQuantity: Long? = null,
    val dueDate: String = "",
    val dueOdometer: Long? = null,
    val metric: String = "Both",
    val isEquipped: Boolean = true,
    val noteText: String? = null,
    val pinned: Boolean = false,
)

/**
 * One queued attachment. For a [QueuedEntry.pendingAttachments] item, [localFileName] names the
 * file within the entry's directory (or the staging directory, before the entry is committed);
 * for an [QueuedEntry.uploadedAttachments] item, [location] is the server-side location returned at
 * upload time and [localFileName] is unused.
 */
data class QueuedAttachment(
    val fileName: String,
    val mimeType: String,
    val localFileName: String? = null,
    val location: String? = null,
)
