package com.safemode.llconnect.ui.records

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.queue.QueuedAttachment
import com.safemode.llconnect.data.queue.QueuedFields
import com.safemode.llconnect.data.remote.models.ExtraField
import com.safemode.llconnect.data.remote.models.FileAttachment
import com.safemode.llconnect.data.NumberParsing
import com.safemode.llconnect.ui.common.DateField
import com.safemode.llconnect.ui.common.DropdownField
import com.safemode.llconnect.ui.common.FileAttachmentListSaver
import com.safemode.llconnect.ui.common.NullableExtraFieldListSaver
import com.safemode.llconnect.ui.common.QueuedAttachmentListSaver
import com.safemode.llconnect.ui.scan.ScanReceiptScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

private val PlanTypes = listOf("ServiceRecord", "RepairRecord", "UpgradeRecord")
private val PlanPriorities = listOf("Low", "Normal", "Critical")
private val PlanProgress = listOf("Backlog", "InProgress", "Testing")
private val ReminderMetrics = listOf("Both", "Odometer", "Date")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFormScreen(
    vehicleId: String,
    areaName: String,
    recordId: String,
    onBack: () -> Unit,
    onAttachments: (String) -> Unit,
) {
    val area = remember(areaName) { recordAreaFromName(areaName) }
    val isEdit = recordId.isNotBlank()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Display-only odometer unit label (e.g. "mi"), from the Distance unit setting. The mapped
    // flow is remembered so it's built once, not rebuilt on every recomposition.
    val distanceUnitFlow = remember { Graph.settingsRepository.config.map { it.distanceUnit.suffix } }
    val distanceUnit by distanceUnitFlow
        .collectAsStateWithLifecycle(initialValue = Graph.apiProvider.currentConfig().distanceUnit.suffix)

    // Form state is saveable so rotation, a theme change, or the process being killed in the
    // background doesn't wipe what's been typed or scanned. Saving, deleting and scan uploads run
    // in [RecordFormViewModel], so they carry on through a recreation instead of being cancelled.
    val formViewModel: RecordFormViewModel = viewModel(key = "record-form-$vehicleId-$areaName-$recordId")
    val work by formViewModel.work.collectAsStateWithLifecycle()
    val submitting = work == RecordFormViewModel.Work.SAVING
    val deleting = work == RecordFormViewModel.Work.DELETING
    val scanUploading = work == RecordFormViewModel.Work.UPLOADING_SCAN
    val busy = work != null
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var odometer by rememberSaveable { mutableStateOf("") }
    // Odometer records: the reading the entry counts up from. Prefilled (carried from the
    // previous entry when adding, or the record's own value when editing) and read-only until
    // the user taps the edit toggle beside it.
    var initialOdometer by rememberSaveable { mutableStateOf("") }
    var initialOdometerLocked by rememberSaveable { mutableStateOf(true) }
    var description by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var fuelConsumed by rememberSaveable { mutableStateOf("") }
    var isFillToFull by rememberSaveable { mutableStateOf(true) }
    var missedFuelUp by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    // Planner
    var planType by rememberSaveable { mutableStateOf("ServiceRecord") }
    var priority by rememberSaveable { mutableStateOf("Normal") }
    var progress by rememberSaveable { mutableStateOf("Backlog") }
    // Supplies
    var partNumber by rememberSaveable { mutableStateOf("") }
    var partSupplier by rememberSaveable { mutableStateOf("") }
    var partQuantity by rememberSaveable { mutableStateOf("") }
    // Reminders
    var dueDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var dueOdometer by rememberSaveable { mutableStateOf("") }
    var metric by rememberSaveable { mutableStateOf("Both") }
    // Equipment
    var isEquipped by rememberSaveable { mutableStateOf(true) }
    // Notes
    var noteText by rememberSaveable { mutableStateOf("") }
    var pinned by rememberSaveable { mutableStateOf(false) }
    // Not user-editable here, but preserved so an update doesn't wipe them server-side.
    var attachments by rememberSaveable(stateSaver = FileAttachmentListSaver) {
        mutableStateOf<List<FileAttachment>>(emptyList())
    }
    // The attachments as loaded when an edit opened, so a save can tell what the user added or
    // removed here apart from what changed on the server since.
    var loadedAttachments by rememberSaveable(stateSaver = FileAttachmentListSaver) {
        mutableStateOf<List<FileAttachment>>(emptyList())
    }
    var extraFields by rememberSaveable(stateSaver = NullableExtraFieldListSaver) {
        mutableStateOf<List<ExtraField>?>(null)
    }
    // Receipts whose upload couldn't happen right away: held locally (staged). A new record uploads
    // them when it's flushed from the offline queue; an edit uploads them when it's saved.
    var pendingImages by rememberSaveable(stateSaver = QueuedAttachmentListSaver) {
        mutableStateOf<List<QueuedAttachment>>(emptyList())
    }
    // Snapshot of the vehicle's name, so a queued entry reads well even while the server is down.
    var vehicleName by rememberSaveable { mutableStateOf("") }

    // Set once an edit's record has been loaded, so a recreated screen doesn't reload it over the
    // user's changes.
    var editLoaded by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(isEdit) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }

    // In edit mode, fetch the existing record and pre-fill the fields.
    LaunchedEffect(recordId) {
        if (!isEdit || editLoaded) return@LaunchedEffect
        loading = true
        Graph.repository.getRecordForEdit(area, vehicleId, recordId).fold(
            onSuccess = { data ->
                if (data != null) {
                    date = data.date?.ifBlank { date } ?: date
                    odometer = data.odometer?.toString() ?: ""
                    initialOdometer = data.initialOdometer?.toString() ?: ""
                    description = data.description.orEmpty()
                    cost = data.cost?.toString() ?: ""
                    fuelConsumed = data.fuelConsumed?.toString() ?: ""
                    isFillToFull = data.isFillToFull ?: true
                    missedFuelUp = data.missedFuelUp ?: false
                    notes = data.notes.orEmpty()
                    tags = data.tags.orEmpty()
                    data.type?.let { planType = it }
                    data.priority?.let { priority = it }
                    data.progress?.let { progress = it }
                    partNumber = data.partNumber.orEmpty()
                    partSupplier = data.partSupplier.orEmpty()
                    partQuantity = data.partQuantity?.toString() ?: ""
                    dueDate = data.dueDate?.ifBlank { dueDate } ?: dueDate
                    dueOdometer = data.dueOdometer?.toString() ?: ""
                    data.metric?.let { metric = it }
                    isEquipped = data.isEquipped ?: true
                    noteText = data.noteText.orEmpty()
                    pinned = data.pinned ?: false
                    attachments = data.files
                    loadedAttachments = data.files
                    extraFields = data.extraFields
                    editLoaded = true
                } else {
                    error = "Could not load this record for editing."
                }
                loading = false
            },
            onFailure = {
                error = it.message
                loading = false
            },
        )
    }

    // Results of the ViewModel's work, applied to whichever composition is current.
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(formViewModel) {
        formViewModel.outcomes.collect { outcome ->
            when (outcome) {
                is RecordFormViewModel.Outcome.Closed -> {
                    if (outcome.queued) Toast.makeText(context, QUEUED_MESSAGE, Toast.LENGTH_LONG).show()
                    currentOnBack()
                }
                is RecordFormViewModel.Outcome.Failed -> error = outcome.message
                is RecordFormViewModel.Outcome.ScanUploaded -> attachments = attachments + outcome.attachment
                is RecordFormViewModel.Outcome.ScanStaged -> pendingImages = pendingImages + outcome.attachment
                is RecordFormViewModel.Outcome.StagedUploaded -> {
                    attachments = attachments + outcome.attachment
                    pendingImages = pendingImages - outcome.staged
                }
            }
        }
    }

    // Vehicle name for queued-entry display. Reads from cache when the server is unreachable.
    LaunchedEffect(vehicleId) {
        Graph.repository.getVehicleById(vehicleId).onSuccess { v ->
            v?.displayName?.let { if (it.isNotBlank()) vehicleName = it }
        }
    }

    // New odometer record: carry the previous entry's reading over as the initial odometer.
    LaunchedEffect(area, isEdit) {
        if (isEdit || area != RecordArea.ODOMETER) return@LaunchedEffect
        Graph.repository.latestOdometer(vehicleId).onSuccess { last ->
            if (last != null && initialOdometer.isBlank()) initialOdometer = last.toString()
        }
    }

    val showDate = area in setOf(
        RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE,
        RecordArea.GAS, RecordArea.ODOMETER, RecordArea.TAX, RecordArea.SUPPLY,
    )
    val showOdometer = area in setOf(
        RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE,
        RecordArea.GAS, RecordArea.ODOMETER,
    )
    val showDescription = area !in setOf(RecordArea.GAS, RecordArea.ODOMETER)
    val showCost = area in setOf(
        RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE,
        RecordArea.TAX, RecordArea.GAS, RecordArea.PLAN, RecordArea.SUPPLY,
    )
    val showTags = area != RecordArea.PLAN
    val showGas = area == RecordArea.GAS
    val verb = if (isEdit) "Edit" else "Add"

    // The receipt scanner renders in place (not as a separate destination) so the form stays in
    // composition and the entered field values are preserved while scanning.
    if (scanning) {
        BackHandler { scanning = false }
        ScanReceiptScreen(
            quality = Graph.apiProvider.currentConfig().receiptQuality(area),
            onBack = { scanning = false },
            onResult = { path ->
                scanning = false
                error = null
                // The scanner names the cache file with the encoded format's extension (jpg when
                // compressed, png when full quality); mirror it in the upload.
                val ext = path.substringAfterLast('.', "jpg").lowercase()
                val mime = if (ext == "png") "image/png" else "image/jpeg"
                val taken = (attachments.mapNotNull { it.name } + pendingImages.map { it.fileName })
                    .map { it.lowercase() }.toSet()
                formViewModel.uploadScan(path, receiptFileName(date, area, odometer, taken, ext), mime)
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$verb ${area.label.lowercase().trimEnd('s')} record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Attachment management and delete live here, on the record's own screen.
                    if (isEdit && area.supportsAttachments) {
                        IconButton(onClick = { onAttachments(recordId) }) {
                            Icon(Icons.Filled.AttachFile, contentDescription = "Attachments")
                        }
                    }
                    if (isEdit) {
                        IconButton(enabled = !busy, onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showDate) {
                DateField(label = "Date", value = date, onValueChange = { date = it })
            }
            if (area == RecordArea.ODOMETER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = initialOdometer,
                        onValueChange = { initialOdometer = it.filter(Char::isDigit) },
                        label = { Text("Initial odometer ($distanceUnit)") },
                        singleLine = true,
                        // Disabled (grayed) while locked, so it's clearly read-only; the edit
                        // toggle beside it unlocks editing.
                        enabled = !initialOdometerLocked,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                if (initialOdometerLocked) "Carried from the previous entry - tap edit to change"
                                else "Editing",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { initialOdometerLocked = !initialOdometerLocked }) {
                        Icon(
                            imageVector = if (initialOdometerLocked) Icons.Filled.Edit else Icons.Filled.Check,
                            contentDescription = if (initialOdometerLocked) "Edit initial odometer" else "Done editing",
                        )
                    }
                }
            }
            if (showOdometer) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter(Char::isDigit) },
                    label = { Text("Odometer ($distanceUnit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showDescription) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Planner ----
            if (area == RecordArea.PLAN) {
                DropdownField(label = "Type", options = PlanTypes, value = planType, onValueChange = { planType = it })
                DropdownField(label = "Priority", options = PlanPriorities, value = priority, onValueChange = { priority = it })
                DropdownField(label = "Progress", options = PlanProgress, value = progress, onValueChange = { progress = it })
            }

            // ---- Supplies ----
            if (area == RecordArea.SUPPLY) {
                OutlinedTextField(
                    value = partNumber,
                    onValueChange = { partNumber = it },
                    label = { Text("Part number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = partSupplier,
                    onValueChange = { partSupplier = it },
                    label = { Text("Supplier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = partQuantity,
                    onValueChange = { partQuantity = it.filter(Char::isDigit) },
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Reminders ----
            if (area == RecordArea.REMINDER) {
                DropdownField(label = "Metric", options = ReminderMetrics, value = metric, onValueChange = { metric = it })
                if (metric != "Odometer") {
                    DateField(label = "Due date", value = dueDate, onValueChange = { dueDate = it })
                }
                if (metric != "Date") {
                    OutlinedTextField(
                        value = dueOdometer,
                        onValueChange = { dueOdometer = it.filter(Char::isDigit) },
                        label = { Text("Due odometer ($distanceUnit)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- Gas ----
            if (showGas) {
                OutlinedTextField(
                    value = fuelConsumed,
                    onValueChange = { fuelConsumed = it.filter(NumberParsing::isDecimalInputChar) },
                    label = { Text("Fuel consumed") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Fill to full", isFillToFull) { isFillToFull = it }
                ToggleRow("Missed a previous fuel-up", missedFuelUp) { missedFuelUp = it }
            }

            if (showCost) {
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter(NumberParsing::isDecimalInputChar) },
                    label = { Text("Cost") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Equipment ----
            if (area == RecordArea.EQUIPMENT) {
                ToggleRow("Currently equipped", isEquipped) { isEquipped = it }
            }

            // ---- Notes ----
            if (area == RecordArea.NOTE) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note text") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Pinned", pinned) { pinned = it }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (showTags) {
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (space-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Receipt/document scanning, available on any area that carries attachments.
            if (area.supportsAttachments) {
                ReceiptSection(
                    attachments = attachments,
                    pending = pendingImages,
                    uploading = scanUploading,
                    onScan = { scanning = true },
                    scanEnabled = !busy,
                    onRemove = { file -> attachments = attachments - file },
                    onRemovePending = { p ->
                        pendingImages = pendingImages - p
                        scope.launch { Graph.offlineQueue.discardStaged(p) }
                    },
                )
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    error = null
                    if (isEdit) {
                        formViewModel.saveEdit(
                            area = area,
                            vehicleId = vehicleId,
                            recordId = recordId,
                            values = FormValues(
                                date = date,
                                odometer = odometer.toLongOrNull(),
                                initialOdometer = initialOdometer.toLongOrNull(),
                                description = description.ifBlank { null },
                                cost = NumberParsing.parseDecimal(cost),
                                fuelConsumed = NumberParsing.parseDecimal(fuelConsumed),
                                isFillToFull = isFillToFull,
                                missedFuelUp = missedFuelUp,
                                // Send empty strings rather than null so notes/tags are always
                                // present in the form body, matching LubeLogger's web UI.
                                notes = notes,
                                tags = tags,
                                planType = planType,
                                priority = priority,
                                progress = progress,
                                partNumber = partNumber.ifBlank { null },
                                partSupplier = partSupplier.ifBlank { null },
                                partQuantity = partQuantity.toLongOrNull(),
                                dueDate = dueDate,
                                dueOdometer = dueOdometer.toLongOrNull(),
                                metric = metric,
                                isEquipped = isEquipped,
                                noteText = noteText.ifBlank { null },
                                pinned = pinned,
                                files = attachments,
                                extraFields = extraFields,
                            ),
                            loadedAttachments = loadedAttachments,
                            pending = pendingImages,
                        )
                    } else {
                        formViewModel.saveNew(
                            area = area,
                            vehicleId = vehicleId,
                            vehicleName = vehicleName.ifBlank { "Vehicle #$vehicleId" },
                            fields = QueuedFields(
                                date = date,
                                odometer = odometer.toLongOrNull(),
                                initialOdometer = initialOdometer.toLongOrNull(),
                                description = description.ifBlank { null },
                                cost = NumberParsing.parseDecimal(cost),
                                fuelConsumed = NumberParsing.parseDecimal(fuelConsumed),
                                isFillToFull = isFillToFull,
                                missedFuelUp = missedFuelUp,
                                notes = notes,
                                tags = tags,
                                planType = planType,
                                priority = priority,
                                progress = progress,
                                partNumber = partNumber.ifBlank { null },
                                partSupplier = partSupplier.ifBlank { null },
                                partQuantity = partQuantity.toLongOrNull(),
                                dueDate = dueDate,
                                dueOdometer = dueOdometer.toLongOrNull(),
                                metric = metric,
                                isEquipped = isEquipped,
                                noteText = noteText.ifBlank { null },
                                pinned = pinned,
                            ),
                            attachments = attachments,
                            pending = pendingImages,
                        )
                    }
                },
                // Not while a scan is still uploading either, so the save can't miss it.
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (isEdit) "Update record" else "Save record")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text("Delete record?") },
            text = { Text("This permanently deletes this record on the server. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        confirmDelete = false
                        error = null
                        formViewModel.delete(area, recordId)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * "Scan receipt" control plus the list of attachments on this record, each removable. The scan
 * launches the OpenCV document scanner; the resulting image is uploaded and added to [attachments].
 */
@Composable
private fun ReceiptSection(
    attachments: List<FileAttachment>,
    pending: List<QueuedAttachment>,
    uploading: Boolean,
    scanEnabled: Boolean,
    onScan: () -> Unit,
    onRemove: (FileAttachment) -> Unit,
    onRemovePending: (QueuedAttachment) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = onScan,
            enabled = scanEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                Text("  Scan receipt")
            }
        }
        attachments.forEach { file ->
            AttachmentRow(
                name = file.name?.ifBlank { null } ?: "Attachment",
                caption = null,
                onRemove = { onRemove(file) },
            )
        }
        // Receipts scanned while offline: not yet uploaded, shown so the user knows they'll go up
        // with the record once it flushes from the queue.
        pending.forEach { p ->
            AttachmentRow(
                name = p.fileName,
                caption = "Pending upload",
                onRemove = { onRemovePending(p) },
            )
        }
    }
}

@Composable
private fun AttachmentRow(name: String, caption: String?, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Filled.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodySmall)
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove $name")
        }
    }
}

/**
 * Builds the scanned receipt's filename from the record's own fields:
 * `date_recordtype_odometer_receipt.jpg` when an odometer reading is present (e.g.
 * 2026-10-02_service_22148_receipt.jpg), or `date_recordtype_receipt.jpg` when it isn't (e.g. a
 * scan taken before the form is filled in). The date falls back to today when the field is blank.
 *
 * When a record already carries an attachment with that name - multiple receipts scanned onto the
 * same record share every field above - a numeric suffix is appended (`..._receipt_2.jpg`,
 * `..._receipt_3.jpg`, ...) so each upload keeps a distinct name instead of colliding.
 */
private fun receiptFileName(
    date: String,
    area: RecordArea,
    odometer: String,
    taken: Set<String>,
    extension: String,
): String {
    val datePart = date.ifBlank { LocalDate.now().toString() }
    val type = area.name.lowercase()
    val odo = odometer.trim()
    val base = if (odo.isNotBlank()) "${datePart}_${type}_${odo}_receipt"
    else "${datePart}_${type}_receipt"
    var candidate = "$base.$extension"
    var n = 2
    while (candidate.lowercase() in taken) {
        candidate = "${base}_$n.$extension"
        n++
    }
    return candidate
}

/** Shown after a new record is saved to the offline queue. */
private const val QUEUED_MESSAGE =
    "Saved to queue - it'll upload when the server is reachable"
