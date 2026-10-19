package com.safemode.llconnect.data

import com.safemode.llconnect.data.remote.ApiProvider
import com.safemode.llconnect.data.remote.FormEncoder
import com.safemode.llconnect.data.remote.LubeLoggerApi
import com.safemode.llconnect.data.remote.models.FileAttachment
import com.safemode.llconnect.data.remote.models.FileAttachmentResponse
import com.safemode.llconnect.data.remote.models.EquipmentRecordRequest
import com.safemode.llconnect.data.remote.models.GasRecordRequest
import com.safemode.llconnect.data.remote.models.GenericRecordRequest
import com.safemode.llconnect.data.remote.models.NoteRequest
import com.safemode.llconnect.data.remote.models.OdometerRecordRequest
import com.safemode.llconnect.data.remote.models.PlanRecordRequest
import com.safemode.llconnect.data.remote.models.ReminderRecordRequest
import com.safemode.llconnect.data.remote.models.SupplyRecordRequest
import com.safemode.llconnect.data.remote.models.TaxRecordRequest
import com.safemode.llconnect.data.remote.models.Vehicle
import com.safemode.llconnect.data.remote.models.VehicleAddRequest
import com.safemode.llconnect.data.remote.models.VehicleUpdateRequest
import com.safemode.llconnect.data.remote.models.WhoAmI
import com.safemode.llconnect.data.queue.QueuedFields
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import com.safemode.llconnect.data.remote.models.GasRecord
import com.safemode.llconnect.data.remote.models.GenericRecord
import com.safemode.llconnect.data.remote.models.OdometerRecord
import com.safemode.llconnect.data.settings.FuelEconomyUnit
import com.safemode.llconnect.data.settings.RecordSortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.time.LocalDate

/**
 * Single entry point the UI uses to talk to LubeLogger. Wraps API calls in
 * [Result] and maps the various record types onto a shared [RecordRow] shape.
 */
class LubeLoggerRepository(private val apiProvider: ApiProvider) {

    private fun api(): LubeLoggerApi = apiProvider.apiOrNull()
        ?: throw IllegalStateException("Connection not configured. Add your server details in Settings.")

    private suspend fun <T> call(block: suspend (LubeLoggerApi) -> Response<T>): Result<T> =
        runCatchingCancellable {
            val response = block(api())
            if (response.isSuccessful) {
                response.body() ?: throw IllegalStateException("Empty response from server.")
            } else {
                throw mapError(response.code(), response.errorBody()?.string())
            }
        }

    /** For endpoints where we only care about success/failure, not the body. */
    private suspend fun callUnit(block: suspend (LubeLoggerApi) -> Response<*>): Result<Unit> =
        runCatchingCancellable {
            val response = block(api())
            if (response.isSuccessful) Unit
            else throw mapError(response.code(), response.errorBody()?.string())
        }

    private fun mapError(code: Int, body: String?): Exception {
        val detail = body?.take(200)?.ifBlank { null }
        return when (code) {
            401 -> IllegalStateException("Unauthorized (401). Check your API key or credentials.")
            403 -> IllegalStateException("Forbidden (403). This account lacks access.")
            404 -> IllegalStateException("Not found (404). Check the server address.")
            // The offline interceptor returns 504 when a request can't reach the server and nothing
            // is cached, so present it as unreachable rather than a raw gateway error.
            504 -> IllegalStateException("Server unreachable. Check your connection and server address.")
            else -> IllegalStateException("Server error $code${detail?.let { ": $it" } ?: ""}")
        }
    }

    // ---- Vehicles ----
    suspend fun getVehicles(): Result<List<Vehicle>> = call { it.getVehicles() }

    suspend fun getVehicleInfo(vehicleId: String): Result<Vehicle?> =
        call { it.getVehicleInfo(vehicleId) }.map { it.firstOrNull() }

    /**
     * Fetches a single vehicle from the vehicle list (the same source the garage list uses),
     * which is more consistent across instances than /api/vehicle/info. Falls back to
     * /api/vehicle/info if the id isn't found.
     */
    suspend fun getVehicleById(vehicleId: String): Result<Vehicle?> = runCatchingCancellable {
        val api = api()
        val fromList = api.getVehicles().unwrap().firstOrNull { it.id?.toString() == vehicleId }
        fromList ?: api.getVehicleInfo(vehicleId).unwrap().firstOrNull()
    }

    suspend fun addVehicle(body: VehicleAddRequest): Result<Unit> =
        callUnit { it.addVehicle(FormEncoder.fields(body)) }

    suspend fun updateVehicle(body: VehicleUpdateRequest): Result<Unit> =
        callUnit { it.updateVehicle(FormEncoder.fields(body)) }

    suspend fun deleteVehicle(id: String): Result<Unit> = callUnit { it.deleteVehicle(id) }

    // ---- System / user ----
    suspend fun whoAmI(): Result<WhoAmI> = call { it.whoAmI() }.map { parseWhoAmI(it.string()) }

    /** Parses the whoami body tolerantly, matching keys regardless of camel/Pascal casing. */
    private fun parseWhoAmI(raw: String): WhoAmI {
        val obj = runCatchingCancellable { JSONObject(raw.trim()) }.getOrNull() ?: return WhoAmI()
        fun find(vararg names: String): String? {
            for (key in obj.keys()) {
                if (names.any { it.equals(key, ignoreCase = true) }) {
                    val value = obj.optString(key)
                    if (value.isNotBlank() && value != "null") return value
                }
            }
            return null
        }
        fun findBool(vararg names: String): Boolean? {
            for (key in obj.keys()) {
                if (names.any { it.equals(key, ignoreCase = true) }) return obj.optBoolean(key)
            }
            return null
        }
        return WhoAmI(
            id = find("id"),
            userName = find("userName", "username"),
            emailAddress = find("emailAddress", "email"),
            isAdmin = findBool("isAdmin", "admin"),
            isRootUser = findBool("isRootUser", "isRoot", "root"),
        )
    }

    suspend fun version(): Result<String> =
        call { it.version() }.map { parseVersion(it.string()) }

    /**
     * The version endpoint may reply with a bare string or a JSON object
     * (e.g. {"version":"1.7.3"}). Extract just the version number for display.
     */
    private fun parseVersion(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return "—"
        if (text.startsWith("{")) {
            runCatching { JSONObject(text) }.getOrNull()?.let { obj ->
                // Prefer common keys, then fall back to any version-looking value.
                for (key in listOf("version", "Version", "currentVersion", "current")) {
                    obj.optString(key).takeIf { it.isNotBlank() }?.let { return it.trim() }
                }
                for (key in obj.keys()) {
                    val value = obj.optString(key).trim()
                    if (value.matches(Regex(".*\\d+\\.\\d+.*"))) return value
                }
            }
        }
        return text.trim('"')
    }

    suspend fun serverInfo(): Result<String> = call { it.serverInfo() }.map { it.string() }

    suspend fun makeBackup(): Result<String> = call { it.makeBackup() }.map { it.string() }

    // ---- Tools ----
    suspend fun cleanup(deep: Boolean): Result<String> =
        call { it.cleanup(if (deep) "true" else null) }.map { summarizeResult(it.string()) }

    suspend fun tempFiles(): Result<String> = call { it.tempFiles() }.map { summarizeResult(it.string()) }

    suspend fun sendReminders(): Result<String> =
        call { it.sendReminders() }.map { summarizeResult(it.string()) }

    private fun summarizeResult(raw: String): String {
        val text = raw.trim()
        return when {
            text.isEmpty() -> "Done."
            text.startsWith("[") -> {
                val count = runCatchingCancellable { JSONArray(text).length() }.getOrDefault(0)
                if (count == 0) "No files." else "$count file${if (count == 1) "" else "s"}."
            }
            else -> text.trim('"').take(200)
        }
    }

    // ---- Cross-vehicle aggregates ----

    /** Maps vehicle id → display name for labeling cross-vehicle rows. */
    private suspend fun vehicleNames(api: LubeLoggerApi): Map<Long?, String> =
        api.getVehicles().unwrap().associate { it.id to it.displayName }

    /** Resolves the current fuel-economy setting into (useMPG, useUKMPG) query values. */
    private fun mpgParams(): Pair<String?, String?> = when (apiProvider.currentConfig().fuelEconomyUnit) {
        FuelEconomyUnit.US_MPG -> "true" to null
        FuelEconomyUnit.UK_MPG -> null to "true"
        FuelEconomyUnit.DEFAULT -> null to null
    }

    /** Configured odometer label suffix (e.g. "mi" or "km") for display-only unit labels. */
    private fun distanceSuffix(): String = apiProvider.currentConfig().distanceUnit.suffix

    /** Recent activity across every vehicle, newest first, optionally within a date range. */
    suspend fun getActivity(
        startDate: String? = null,
        endDate: String? = null,
    ): Result<List<ActivityItem>> = runCatchingCancellable {
        coroutineScope {
            val api = api()
            val names = vehicleNames(api)
            fun name(id: Long?) = names[id] ?: "Vehicle #${id ?: "?"}"
            val (useMpg, useUkMpg) = mpgParams()
            val unit = distanceSuffix()

            val service = async { api.getAllServiceRecords(startDate, endDate).unwrap().map { it.toActivity(RecordArea.SERVICE, ::name) } }
            val repair = async { api.getAllRepairRecords(startDate, endDate).unwrap().map { it.toActivity(RecordArea.REPAIR, ::name) } }
            val upgrade = async { api.getAllUpgradeRecords(startDate, endDate).unwrap().map { it.toActivity(RecordArea.UPGRADE, ::name) } }
            val tax = async { api.getAllTaxRecords(startDate, endDate).unwrap().map { it.toActivity(RecordArea.TAX, ::name) } }
            val gas = async { api.getAllGasRecords(startDate, endDate, useMpg, useUkMpg).unwrap().map { it.toActivity(::name) } }
            val odo = async { api.getAllOdometerRecords(startDate, endDate).unwrap().map { it.toActivity(unit, ::name) } }

            (service.await() + repair.await() + upgrade.await() + tax.await() + gas.await() + odo.await())
                .sortedByDescending { parseDate(it.date)?.toEpochDay() ?: Long.MIN_VALUE }
        }
    }

    /** Aggregated spend derived from the activity feed, optionally within a date range. */
    suspend fun getCostReport(
        startDate: String? = null,
        endDate: String? = null,
    ): Result<CostReport> = getActivity(startDate, endDate).map { items ->
        val withCost = items.filter { (it.cost ?: 0.0) != 0.0 }
        val byCategory = withCost.groupBy { it.area }
            .map { (area, rows) -> CategoryTotal(area, rows.sumOf { it.cost ?: 0.0 }, rows.size) }
            .sortedByDescending { it.total }
        val byVehicle = withCost.groupBy { it.vehicleName }
            .map { (nm, rows) -> VehicleTotal(nm, rows.sumOf { it.cost ?: 0.0 }, rows.size) }
            .sortedByDescending { it.total }
        CostReport(
            totalCost = withCost.sumOf { it.cost ?: 0.0 },
            recordCount = items.size,
            byCategory = byCategory,
            byVehicle = byVehicle,
        )
    }

    /** All reminders across vehicles, soonest due first. */
    suspend fun getAllReminders(): Result<List<ReminderItem>> = runCatchingCancellable {
        coroutineScope {
            val api = api()
            val names = vehicleNames(api)
            api.getAllReminders().unwrap().map { r ->
                ReminderItem(
                    vehicleName = names[r.vehicleId] ?: "Vehicle #${r.vehicleId ?: "?"}",
                    description = r.description?.ifBlank { "(reminder)" } ?: "(reminder)",
                    dueDate = r.dueDate,
                    dueOdometer = r.dueOdometer,
                    urgency = r.urgency,
                    metric = r.metric,
                )
            }.sortedBy { parseDate(it.dueDate)?.toEpochDay() ?: Long.MAX_VALUE }
        }
    }

    private fun parseDate(raw: String?): LocalDate? = DateParsing.parse(raw)

    /**
     * Sorts by the given date field, in the direction chosen in settings (default oldest-first).
     * LubeLogger returns records in insertion order, so a back-dated entry would otherwise land
     * at the bottom; this keeps the list ordered by date. Stable, so same-date records keep their
     * server order; undated/unparseable ones always sort to the end regardless of direction.
     */
    private fun <T> List<T>.byDate(date: (T) -> String?): List<T> {
        val newestFirst =
            apiProvider.currentConfig().recordSortOrder == RecordSortOrder.NEWEST_FIRST
        val keyed = map { it to parseDate(date(it))?.toEpochDay() }
        return keyed
            .sortedWith(compareBy(nullsLast()) { (_, day) ->
                when {
                    day == null -> null            // undated → end
                    newestFirst -> -day            // larger (newer) first
                    else -> day                    // smaller (older) first
                }
            })
            .map { it.first }
    }

    // ---- Records: read ----
    suspend fun getRecords(area: RecordArea, vehicleId: String): Result<List<RecordRow>> =
        runCatchingCancellable {
            val api = api()
            val unit = distanceSuffix()
            when (area) {
                RecordArea.SERVICE -> api.getServiceRecords(vehicleId).unwrap().byDate { it.date }.map { it.toRow(unit) }
                RecordArea.REPAIR -> api.getRepairRecords(vehicleId).unwrap().byDate { it.date }.map { it.toRow(unit) }
                RecordArea.UPGRADE -> api.getUpgradeRecords(vehicleId).unwrap().byDate { it.date }.map { it.toRow(unit) }
                RecordArea.TAX -> api.getTaxRecords(vehicleId).unwrap().byDate { it.date }.map { it.toRow(unit, isTax = true) }
                RecordArea.GAS -> {
                    val (useMpg, useUkMpg) = mpgParams()
                    api.getGasRecords(vehicleId, useMpg, useUkMpg).unwrap().byDate { it.date }.map { it.toRow(unit) }
                }
                RecordArea.ODOMETER -> api.getOdometerRecords(vehicleId).unwrap().byDate { it.date }.map { it.toRow(unit) }
                RecordArea.PLAN -> api.getPlanRecords(vehicleId).unwrap()
                    .byDate { it.dateModified ?: it.dateCreated }.map { it.toRow() }
                RecordArea.SUPPLY -> api.getSupplyRecords(vehicleId).unwrap().byDate { it.date }.map { it.toRow() }
                RecordArea.REMINDER -> api.getReminders(vehicleId).unwrap().map { it.toRow(unit) }
                RecordArea.EQUIPMENT -> api.getEquipmentRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.NOTE -> api.getNotes(vehicleId).unwrap().map { it.toRow() }
            }
        }

    /**
     * Highest existing odometer reading for a vehicle, used to prefill the "initial odometer"
     * on a new odometer record (LubeLogger's web UI carries the previous entry over the same way).
     */
    suspend fun latestOdometer(vehicleId: String): Result<Long?> =
        runCatchingCancellable {
            api().getOdometerRecords(vehicleId).unwrap().mapNotNull { it.odometer }.maxOrNull()
        }

    /** Count + most-recent (or soonest-due, for reminders) date for a single area. */
    suspend fun getAreaSummary(area: RecordArea, vehicleId: String): Result<AreaSummary> =
        runCatchingCancellable {
            val api = api()
            fun <T> List<T>.summarize(earliest: Boolean = false, date: (T) -> String?) =
                AreaSummary(size, reduceDate(mapNotNull(date), earliest))
            when (area) {
                RecordArea.SERVICE -> api.getServiceRecords(vehicleId).unwrap().summarize { it.date }
                RecordArea.REPAIR -> api.getRepairRecords(vehicleId).unwrap().summarize { it.date }
                RecordArea.UPGRADE -> api.getUpgradeRecords(vehicleId).unwrap().summarize { it.date }
                RecordArea.TAX -> api.getTaxRecords(vehicleId).unwrap().summarize { it.date }
                RecordArea.GAS -> api.getGasRecords(vehicleId).unwrap().summarize { it.date }
                RecordArea.ODOMETER -> api.getOdometerRecords(vehicleId).unwrap().summarize { it.date }
                RecordArea.PLAN -> api.getPlanRecords(vehicleId).unwrap()
                    .summarize { it.dateModified ?: it.dateCreated }
                RecordArea.SUPPLY -> api.getSupplyRecords(vehicleId).unwrap().summarize { it.date }
                RecordArea.REMINDER -> api.getReminders(vehicleId).unwrap()
                    .summarize(earliest = true) { it.dueDate }
                RecordArea.EQUIPMENT -> api.getEquipmentRecords(vehicleId).unwrap().summarize { null }
                RecordArea.NOTE -> api.getNotes(vehicleId).unwrap().summarize { null }
            }
        }

    /** Reduces a set of date strings to the latest (or earliest) as an ISO date. */
    private fun reduceDate(dates: List<String>, earliest: Boolean): String? {
        val clean = dates
            .map { it.substringBefore('T').substringBefore(' ').trim() }
            .filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        var best: LocalDate? = null
        for (d in clean) {
            val parsed = DateParsing.parse(d) ?: continue
            if (best == null || (if (earliest) parsed.isBefore(best) else parsed.isAfter(best))) {
                best = parsed
            }
        }
        return best?.toString() ?: clean.first()
    }

    // ---- Records: delete ----
    suspend fun deleteRecord(area: RecordArea, id: String): Result<Unit> = callUnit { api ->
        when (area) {
            RecordArea.SERVICE -> api.deleteServiceRecord(id)
            RecordArea.REPAIR -> api.deleteRepairRecord(id)
            RecordArea.UPGRADE -> api.deleteUpgradeRecord(id)
            RecordArea.TAX -> api.deleteTaxRecord(id)
            RecordArea.GAS -> api.deleteGasRecord(id)
            RecordArea.ODOMETER -> api.deleteOdometerRecord(id)
            RecordArea.PLAN -> api.deletePlanRecord(id)
            RecordArea.SUPPLY -> api.deleteSupplyRecord(id)
            RecordArea.REMINDER -> api.deleteReminder(id)
            RecordArea.EQUIPMENT -> api.deleteEquipmentRecord(id)
            RecordArea.NOTE -> api.deleteNote(id)
        }
    }

    // ---- Records: add (core areas) ----
    suspend fun addServiceLike(area: RecordArea, vehicleId: String, body: GenericRecordRequest): Result<Unit> =
        callUnit { api ->
            val fields = FormEncoder.fields(body)
            when (area) {
                RecordArea.SERVICE -> api.addServiceRecord(vehicleId, fields)
                RecordArea.REPAIR -> api.addRepairRecord(vehicleId, fields)
                RecordArea.UPGRADE -> api.addUpgradeRecord(vehicleId, fields)
                else -> throw IllegalArgumentException("Unsupported area for service-like add: $area")
            }
        }

    suspend fun addTax(vehicleId: String, body: TaxRecordRequest): Result<Unit> =
        callUnit { it.addTaxRecord(vehicleId, FormEncoder.fields(body)) }

    suspend fun addGas(vehicleId: String, body: GasRecordRequest): Result<Unit> =
        callUnit { it.addGasRecord(vehicleId, FormEncoder.fields(body)) }

    suspend fun addOdometer(vehicleId: String, body: OdometerRecordRequest): Result<Unit> =
        callUnit { it.addOdometerRecord(vehicleId, FormEncoder.fields(body)) }

    // ---- Records: update (core areas) ----
    suspend fun updateServiceLike(area: RecordArea, body: GenericRecordRequest): Result<Unit> =
        callUnit { api ->
            val fields = FormEncoder.fields(body)
            when (area) {
                RecordArea.SERVICE -> api.updateServiceRecord(fields)
                RecordArea.REPAIR -> api.updateRepairRecord(fields)
                RecordArea.UPGRADE -> api.updateUpgradeRecord(fields)
                else -> throw IllegalArgumentException("Unsupported area for service-like update: $area")
            }
        }

    suspend fun updateTax(body: TaxRecordRequest): Result<Unit> =
        callUnit { it.updateTaxRecord(FormEncoder.fields(body)) }

    suspend fun updateGas(body: GasRecordRequest): Result<Unit> =
        callUnit { it.updateGasRecord(FormEncoder.fields(body)) }

    suspend fun updateOdometer(body: OdometerRecordRequest): Result<Unit> =
        callUnit { it.updateOdometerRecord(FormEncoder.fields(body)) }

    // ---- Records: add/update (planner, supplies, reminders, equipment, notes) ----
    suspend fun addPlan(vehicleId: String, body: PlanRecordRequest): Result<Unit> =
        callUnit { it.addPlanRecord(vehicleId, FormEncoder.fields(body)) }

    suspend fun updatePlan(body: PlanRecordRequest): Result<Unit> =
        callUnit { it.updatePlanRecord(FormEncoder.fields(body)) }

    suspend fun addSupply(vehicleId: String, body: SupplyRecordRequest): Result<Unit> =
        callUnit { it.addSupplyRecord(vehicleId, FormEncoder.fields(body)) }

    suspend fun updateSupply(body: SupplyRecordRequest): Result<Unit> =
        callUnit { it.updateSupplyRecord(FormEncoder.fields(body)) }

    suspend fun addReminder(vehicleId: String, body: ReminderRecordRequest): Result<Unit> =
        callUnit { it.addReminder(vehicleId, FormEncoder.fields(body)) }

    suspend fun updateReminder(body: ReminderRecordRequest): Result<Unit> =
        callUnit { it.updateReminder(FormEncoder.fields(body)) }

    suspend fun addEquipment(vehicleId: String, body: EquipmentRecordRequest): Result<Unit> =
        callUnit { it.addEquipmentRecord(vehicleId, FormEncoder.fields(body)) }

    suspend fun updateEquipment(body: EquipmentRecordRequest): Result<Unit> =
        callUnit { it.updateEquipmentRecord(FormEncoder.fields(body)) }

    suspend fun addNote(vehicleId: String, body: NoteRequest): Result<Unit> =
        callUnit { it.addNote(vehicleId, FormEncoder.fields(body)) }

    suspend fun updateNote(body: NoteRequest): Result<Unit> =
        callUnit { it.updateNote(FormEncoder.fields(body)) }

    // ---- Records: add a new record from queued form values ----

    /**
     * Adds a new record of any area from the generic [QueuedFields], attaching [files]. This is the
     * add-only request construction shared by the record form's new-record path and the offline
     * queue flush, so both build identical requests. Edits are not handled here.
     */
    suspend fun add(
        area: RecordArea,
        vehicleId: String,
        fields: QueuedFields,
        files: List<FileAttachment>,
    ): Result<Unit> {
        val f = files.ifEmpty { null }
        return when (area) {
            RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE -> addServiceLike(
                area, vehicleId,
                GenericRecordRequest(
                    date = fields.date,
                    odometer = fields.odometer,
                    description = fields.description,
                    cost = fields.cost,
                    notes = fields.notes,
                    tags = fields.tags,
                    files = f,
                ),
            )
            RecordArea.TAX -> addTax(
                vehicleId,
                TaxRecordRequest(
                    date = fields.date,
                    description = fields.description,
                    cost = fields.cost,
                    notes = fields.notes,
                    tags = fields.tags,
                    files = f,
                ),
            )
            RecordArea.GAS -> addGas(
                vehicleId,
                GasRecordRequest(
                    date = fields.date,
                    odometer = fields.odometer,
                    fuelConsumed = fields.fuelConsumed,
                    cost = fields.cost,
                    isFillToFull = fields.isFillToFull,
                    missedFuelUp = fields.missedFuelUp,
                    notes = fields.notes,
                    tags = fields.tags,
                    files = f,
                ),
            )
            RecordArea.ODOMETER -> addOdometer(
                vehicleId,
                OdometerRecordRequest(
                    date = fields.date,
                    initialOdometer = fields.initialOdometer,
                    odometer = fields.odometer,
                    notes = fields.notes,
                    tags = fields.tags,
                    files = f,
                ),
            )
            RecordArea.PLAN -> addPlan(
                vehicleId,
                PlanRecordRequest(
                    description = fields.description,
                    cost = fields.cost,
                    type = fields.planType,
                    priority = fields.priority,
                    progress = fields.progress,
                    notes = fields.notes,
                    files = f,
                ),
            )
            RecordArea.SUPPLY -> addSupply(
                vehicleId,
                SupplyRecordRequest(
                    date = fields.date,
                    partNumber = fields.partNumber,
                    partSupplier = fields.partSupplier,
                    partQuantity = fields.partQuantity,
                    description = fields.description,
                    cost = fields.cost,
                    notes = fields.notes,
                    tags = fields.tags,
                    files = f,
                ),
            )
            RecordArea.REMINDER -> addReminder(
                vehicleId,
                ReminderRecordRequest(
                    description = fields.description,
                    dueDate = if (fields.metric != "Odometer") fields.dueDate else null,
                    dueOdometer = if (fields.metric != "Date") fields.dueOdometer else null,
                    metric = fields.metric,
                    notes = fields.notes,
                    tags = fields.tags,
                ),
            )
            RecordArea.EQUIPMENT -> addEquipment(
                vehicleId,
                EquipmentRecordRequest(
                    description = fields.description,
                    isEquipped = fields.isEquipped,
                    notes = fields.notes,
                    tags = fields.tags,
                    files = f,
                ),
            )
            RecordArea.NOTE -> addNote(
                vehicleId,
                NoteRequest(
                    description = fields.description,
                    noteText = fields.noteText,
                    pinned = fields.pinned,
                    tags = fields.tags,
                    files = f,
                ),
            )
        }
    }

    /**
     * Whether the server already holds a record matching [fields] for this vehicle/area, used by
     * the queue flush to avoid creating a second copy when a flush is retried or a write partially
     * succeeded. Matches on the identifying fields per area (a pragmatic "same key fields" check,
     * not a strict equality of every field).
     */
    suspend fun findDuplicateAdd(
        area: RecordArea,
        vehicleId: String,
        fields: QueuedFields,
    ): Result<Boolean> = runCatchingCancellable {
        val api = api()
        when (area) {
            RecordArea.SERVICE -> api.getServiceRecords(vehicleId).unwrap().any {
                sameDate(it.date, fields.date) && sameLong(it.odometer, fields.odometer) &&
                    sameCost(it.cost, fields.cost) && sameText(it.description, fields.description)
            }
            RecordArea.REPAIR -> api.getRepairRecords(vehicleId).unwrap().any {
                sameDate(it.date, fields.date) && sameLong(it.odometer, fields.odometer) &&
                    sameCost(it.cost, fields.cost) && sameText(it.description, fields.description)
            }
            RecordArea.UPGRADE -> api.getUpgradeRecords(vehicleId).unwrap().any {
                sameDate(it.date, fields.date) && sameLong(it.odometer, fields.odometer) &&
                    sameCost(it.cost, fields.cost) && sameText(it.description, fields.description)
            }
            RecordArea.TAX -> api.getTaxRecords(vehicleId).unwrap().any {
                sameDate(it.date, fields.date) && sameCost(it.cost, fields.cost) &&
                    sameText(it.description, fields.description)
            }
            RecordArea.GAS -> api.getGasRecords(vehicleId, null, null).unwrap().any {
                sameDate(it.date, fields.date) && sameLong(it.odometer, fields.odometer) &&
                    sameCost(it.cost, fields.cost) && sameCost(it.fuelConsumed, fields.fuelConsumed)
            }
            RecordArea.ODOMETER -> api.getOdometerRecords(vehicleId).unwrap().any {
                sameDate(it.date, fields.date) && sameLong(it.odometer, fields.odometer)
            }
            RecordArea.PLAN -> api.getPlanRecords(vehicleId).unwrap().any {
                sameText(it.description, fields.description) && sameCost(it.cost, fields.cost) &&
                    sameText(it.type, fields.planType)
            }
            RecordArea.SUPPLY -> api.getSupplyRecords(vehicleId).unwrap().any {
                sameDate(it.date, fields.date) && sameText(it.description, fields.description) &&
                    sameText(it.partNumber, fields.partNumber) && sameCost(it.cost, fields.cost)
            }
            RecordArea.REMINDER -> api.getReminders(vehicleId).unwrap().any {
                sameText(it.description, fields.description) && sameDate(it.dueDate, fields.dueDate) &&
                    sameLong(it.dueOdometer, fields.dueOdometer) && sameText(it.metric, fields.metric)
            }
            RecordArea.EQUIPMENT -> api.getEquipmentRecords(vehicleId).unwrap().any {
                sameText(it.description, fields.description)
            }
            RecordArea.NOTE -> api.getNotes(vehicleId).unwrap().any {
                sameText(it.description, fields.description) && sameText(it.noteText, fields.noteText)
            }
        }
    }

    private fun sameDate(a: String?, b: String?): Boolean {
        val da = parseDate(a)
        val db = parseDate(b)
        return if (da != null && db != null) da == db
        else a?.trim().orEmpty() == b?.trim().orEmpty()
    }

    private fun sameText(a: String?, b: String?): Boolean =
        a?.trim().orEmpty() == b?.trim().orEmpty()

    private fun sameCost(a: Double?, b: Double?): Boolean =
        kotlin.math.abs((a ?: 0.0) - (b ?: 0.0)) < 0.005

    private fun sameLong(a: Long?, b: Long?): Boolean = (a ?: 0L) == (b ?: 0L)

    // ---- Records: fetch a single record for editing ----
    suspend fun getRecordForEdit(
        area: RecordArea,
        vehicleId: String,
        id: String,
    ): Result<RecordEditData?> = runCatchingCancellable {
        val api = api()
        fun match(rowId: Long?) = rowId?.toString() == id
        when (area) {
            RecordArea.SERVICE ->
                api.getServiceRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.REPAIR ->
                api.getRepairRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.UPGRADE ->
                api.getUpgradeRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.TAX ->
                api.getTaxRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.GAS ->
                api.getGasRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.ODOMETER ->
                api.getOdometerRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.PLAN ->
                api.getPlanRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.SUPPLY ->
                api.getSupplyRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.REMINDER ->
                api.getReminders(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.EQUIPMENT ->
                api.getEquipmentRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
            RecordArea.NOTE ->
                api.getNotes(vehicleId).unwrap().firstOrNull { match(it.id) }?.toEdit()
        }
    }

    // ---- Attachments ----

    /** Current file attachments on a record. */
    suspend fun getAttachments(
        area: RecordArea,
        vehicleId: String,
        recordId: String,
    ): Result<List<FileAttachmentResponse>> = runCatchingCancellable {
        val api = api()
        fun match(id: Long?) = id?.toString() == recordId
        when (area) {
            RecordArea.SERVICE -> api.getServiceRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.REPAIR -> api.getRepairRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.UPGRADE -> api.getUpgradeRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.TAX -> api.getTaxRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.GAS -> api.getGasRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.ODOMETER -> api.getOdometerRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.PLAN -> api.getPlanRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.SUPPLY -> api.getSupplyRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.EQUIPMENT -> api.getEquipmentRecords(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.NOTE -> api.getNotes(vehicleId).unwrap().firstOrNull { match(it.id) }?.files
            RecordArea.REMINDER -> null
        }.orEmpty()
    }

    /** Replaces the record's attachment list (used for add/rename/delete), preserving all other fields. */
    suspend fun setAttachments(
        area: RecordArea,
        vehicleId: String,
        recordId: String,
        files: List<FileAttachment>,
    ): Result<Unit> = runCatchingCancellable {
        val api = api()
        fun match(id: Long?) = id?.toString() == recordId
        fun <T> T?.orThrow(): T = this ?: throw IllegalStateException("Record no longer exists.")
        val response: Response<*> = when (area) {
            RecordArea.SERVICE -> api.updateServiceRecord(
                FormEncoder.fields(api.getServiceRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.REPAIR -> api.updateRepairRecord(
                FormEncoder.fields(api.getRepairRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.UPGRADE -> api.updateUpgradeRecord(
                FormEncoder.fields(api.getUpgradeRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.TAX -> api.updateTaxRecord(
                FormEncoder.fields(api.getTaxRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toTaxUpdateRequest(files))
            )
            RecordArea.GAS -> api.updateGasRecord(
                FormEncoder.fields(api.getGasRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.ODOMETER -> api.updateOdometerRecord(
                FormEncoder.fields(api.getOdometerRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.PLAN -> api.updatePlanRecord(
                FormEncoder.fields(api.getPlanRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.SUPPLY -> api.updateSupplyRecord(
                FormEncoder.fields(api.getSupplyRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.EQUIPMENT -> api.updateEquipmentRecord(
                FormEncoder.fields(api.getEquipmentRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.NOTE -> api.updateNote(
                FormEncoder.fields(api.getNotes(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files))
            )
            RecordArea.REMINDER -> throw IllegalArgumentException("Reminders don't support attachments.")
        }
        if (!response.isSuccessful) throw mapError(response.code(), response.errorBody()?.string())
        Unit
    }

    /** Uploads a document and returns a [FileAttachment] pointing at the stored file. */
    suspend fun uploadDocument(
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): Result<FileAttachment> = runCatchingCancellable {
        val media = (mimeType ?: "application/octet-stream").toMediaTypeOrNull()
        finishUpload(MultipartBody.Part.createFormData("documents", fileName, bytes.toRequestBody(media)), fileName)
    }

    /**
     * Streams [file] to the server rather than reading it into memory first, so full-resolution
     * (uncompressed) receipt scans don't risk an out-of-memory crash on large images.
     */
    suspend fun uploadDocument(
        file: File,
        fileName: String,
        mimeType: String?,
    ): Result<FileAttachment> = runCatchingCancellable {
        val media = (mimeType ?: "application/octet-stream").toMediaTypeOrNull()
        finishUpload(MultipartBody.Part.createFormData("documents", fileName, file.asRequestBody(media)), fileName)
    }

    private suspend fun finishUpload(part: MultipartBody.Part, fileName: String): FileAttachment {
        val response = api().uploadDocuments(listOf(part))
        if (!response.isSuccessful) throw mapError(response.code(), response.errorBody()?.string())
        val body = response.body()?.string().orEmpty()
        return parseUploadedLocation(body, fileName)
            ?: throw IllegalStateException("Upload succeeded but no file location was returned.")
    }

    /** Absolute URL for an attachment location, or null if not configured. */
    fun attachmentUrl(location: String?): String? {
        val loc = location?.takeIf { it.isNotBlank() } ?: return null
        if (loc.startsWith("http")) return loc
        val base = apiProvider.currentConfig().baseUrl().trimEnd('/')
        return base + "/" + loc.trimStart('/')
    }

    /** Downloads an attachment's bytes using the authenticated client (off the main thread). */
    suspend fun downloadAttachment(location: String): Result<ByteArray> = runCatchingCancellable {
        val url = attachmentUrl(location) ?: throw IllegalStateException("Not connected.")
        val client = apiProvider.authenticatedImageClient()
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use {
                if (!it.isSuccessful) throw IllegalStateException("Download failed (${it.code}).")
                it.body?.bytes() ?: throw IllegalStateException("Empty download.")
            }
        }
    }

    private fun parseUploadedLocation(body: String, fallbackName: String): FileAttachment? {
        if (body.isBlank()) return null
        return try {
            val trimmed = body.trim()
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) return null
                    when (val first = arr.get(0)) {
                        is JSONObject -> FileAttachment(
                            name = first.optString("name").ifBlank { fallbackName },
                            location = first.optString("location").ifBlank { null },
                        )
                        is String -> FileAttachment(name = fallbackName, location = first)
                        else -> null
                    }
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    FileAttachment(
                        name = obj.optString("name").ifBlank { fallbackName },
                        location = obj.optString("location").ifBlank { null },
                    )
                }
                else -> FileAttachment(name = fallbackName, location = trimmed.trim('"'))
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Response unwrapping ----
    private fun <T> Response<T>.unwrap(): T {
        if (isSuccessful) return body() ?: throw IllegalStateException("Empty response from server.")
        throw mapError(code(), errorBody()?.string())
    }
}
