package com.safemode.llconnect.ui.records

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.remote.models.EquipmentRecordRequest
import com.safemode.llconnect.data.remote.models.GasRecordRequest
import com.safemode.llconnect.data.remote.models.GenericRecordRequest
import com.safemode.llconnect.data.remote.models.NoteRequest
import com.safemode.llconnect.data.remote.models.OdometerRecordRequest
import com.safemode.llconnect.data.remote.models.PlanRecordRequest
import com.safemode.llconnect.data.remote.models.ReminderRecordRequest
import com.safemode.llconnect.data.remote.models.SupplyRecordRequest
import com.safemode.llconnect.data.remote.models.TaxRecordRequest
import com.safemode.llconnect.ui.common.DateField
import com.safemode.llconnect.ui.common.DropdownField
import kotlinx.coroutines.launch
import java.time.LocalDate

private val PlanTypes = listOf("ServiceRecord", "RepairRecord", "UpgradeRecord")
private val PlanPriorities = listOf("Low", "Normal", "Critical")
private val PlanProgress = listOf("Backlog", "InProgress", "Testing")
private val ReminderMetrics = listOf("Both", "Odometer", "Date")

/** All editable form fields, bundled so [submit] stays readable. */
private data class FormValues(
    val date: String,
    val odometer: Long?,
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFormScreen(
    vehicleId: String,
    areaName: String,
    recordId: String,
    onBack: () -> Unit,
) {
    val area = remember(areaName) { recordAreaFromName(areaName) }
    val isEdit = recordId.isNotBlank()
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var odometer by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var fuelConsumed by remember { mutableStateOf("") }
    var isFillToFull by remember { mutableStateOf(true) }
    var missedFuelUp by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    // Planner
    var planType by remember { mutableStateOf("ServiceRecord") }
    var priority by remember { mutableStateOf("Normal") }
    var progress by remember { mutableStateOf("Backlog") }
    // Supplies
    var partNumber by remember { mutableStateOf("") }
    var partSupplier by remember { mutableStateOf("") }
    var partQuantity by remember { mutableStateOf("") }
    // Reminders
    var dueDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var dueOdometer by remember { mutableStateOf("") }
    var metric by remember { mutableStateOf("Both") }
    // Equipment
    var isEquipped by remember { mutableStateOf(true) }
    // Notes
    var noteText by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(isEdit) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // In edit mode, fetch the existing record and pre-fill the fields.
    LaunchedEffect(recordId) {
        if (!isEdit) return@LaunchedEffect
        loading = true
        Graph.repository.getRecordForEdit(area, vehicleId, recordId).fold(
            onSuccess = { data ->
                if (data != null) {
                    date = data.date?.ifBlank { date } ?: date
                    odometer = data.odometer?.toString() ?: ""
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$verb ${area.label.lowercase().trimEnd('s')} record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (showOdometer) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter(Char::isDigit) },
                    label = { Text("Odometer") },
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
                        label = { Text("Due odometer") },
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
                    onValueChange = { fuelConsumed = it.filter { c -> c.isDigit() || c == '.' } },
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
                    onValueChange = { cost = it.filter { c -> c.isDigit() || c == '.' } },
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

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    error = null
                    submitting = true
                    scope.launch {
                        val result = submit(
                            area = area,
                            vehicleId = vehicleId,
                            id = recordId.toLongOrNull().takeIf { isEdit },
                            values = FormValues(
                                date = date,
                                odometer = odometer.toLongOrNull(),
                                description = description.ifBlank { null },
                                cost = cost.toDoubleOrNull(),
                                fuelConsumed = fuelConsumed.toDoubleOrNull(),
                                isFillToFull = isFillToFull,
                                missedFuelUp = missedFuelUp,
                                notes = notes.ifBlank { null },
                                tags = tags.ifBlank { null },
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
                        )
                        submitting = false
                        result.fold(
                            onSuccess = { onBack() },
                            onFailure = { error = it.message },
                        )
                    }
                },
                enabled = !submitting,
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
            )
            if (isEdit) repo.updateGas(body) else repo.addGas(vehicleId, body)
        }
        RecordArea.ODOMETER -> {
            val body = OdometerRecordRequest(
                id = id,
                date = values.date,
                odometer = values.odometer,
                notes = values.notes,
                tags = values.tags,
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
            )
            if (isEdit) repo.updateNote(body) else repo.addNote(vehicleId, body)
        }
    }
}
