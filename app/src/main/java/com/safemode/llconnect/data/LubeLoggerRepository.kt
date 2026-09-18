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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.safemode.llconnect.data.remote.models.GasRecord
import com.safemode.llconnect.data.remote.models.GenericRecord
import com.safemode.llconnect.data.remote.models.OdometerRecord
import com.safemode.llconnect.data.settings.FuelEconomyUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Single entry point the UI uses to talk to LubeLogger. Wraps API calls in
 * [Result] and maps the various record types onto a shared [RecordRow] shape.
 */
class LubeLoggerRepository(private val apiProvider: ApiProvider) {

    private fun api(): LubeLoggerApi = apiProvider.apiOrNull()
        ?: throw IllegalStateException("Connection not configured. Add your server details in Settings.")

    private suspend fun <T> call(block: suspend (LubeLoggerApi) -> Response<T>): Result<T> =
        runCatching {
            val response = block(api())
            if (response.isSuccessful) {
                response.body() ?: throw IllegalStateException("Empty response from server.")
            } else {
                throw mapError(response.code(), response.errorBody()?.string())
            }
        }

    /** For endpoints where we only care about success/failure, not the body. */
    private suspend fun callUnit(block: suspend (LubeLoggerApi) -> Response<*>): Result<Unit> =
        runCatching {
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
    suspend fun getVehicleById(vehicleId: String): Result<Vehicle?> = runCatching {
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
        val obj = runCatching { JSONObject(raw.trim()) }.getOrNull() ?: return WhoAmI()
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
                val count = runCatching { JSONArray(text).length() }.getOrDefault(0)
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

    /** Recent activity across every vehicle, newest first, optionally within a date range. */
    suspend fun getActivity(
        startDate: String? = null,
        endDate: String? = null,
    ): Result<List<ActivityItem>> = runCatching {
        coroutineScope {
            val api = api()
            val names = vehicleNames(api)
            fun name(id: Long?) = names[id] ?: "Vehicle #${id ?: "?"}"
            val (useMpg, useUkMpg) = mpgParams()

            val service = async { api.getAllServiceRecords(startDate, endDate).unwrap().map { it.toActivity(RecordArea.SERVICE, ::name) } }
            val repair = async { api.getAllRepairRecords(startDate, endDate).unwrap().map { it.toActivity(RecordArea.REPAIR, ::name) } }
            val upgrade = async { api.getAllUpgradeRecords(startDate, endDate).unwrap().map { it.toActivity(RecordArea.UPGRADE, ::name) } }
            val tax = async { api.getAllTaxRecords(startDate, endDate).unwrap().map { it.toActivity(RecordArea.TAX, ::name) } }
            val gas = async { api.getAllGasRecords(startDate, endDate, useMpg, useUkMpg).unwrap().map { it.toActivity(::name) } }
            val odo = async { api.getAllOdometerRecords(startDate, endDate).unwrap().map { it.toActivity(::name) } }

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
    suspend fun getAllReminders(): Result<List<ReminderItem>> = runCatching {
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

    private fun parseDate(raw: String?): LocalDate? {
        val d = raw?.substringBefore('T')?.substringBefore(' ')?.trim().orEmpty()
        if (d.isEmpty()) return null
        return DateFormats.firstNotNullOfOrNull { runCatching { LocalDate.parse(d, it) }.getOrNull() }
    }

    // ---- Records: read ----
    suspend fun getRecords(area: RecordArea, vehicleId: String): Result<List<RecordRow>> =
        runCatching {
            val api = api()
            when (area) {
                RecordArea.SERVICE -> api.getServiceRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.REPAIR -> api.getRepairRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.UPGRADE -> api.getUpgradeRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.TAX -> api.getTaxRecords(vehicleId).unwrap().map { it.toRow(isTax = true) }
                RecordArea.GAS -> {
                    val (useMpg, useUkMpg) = mpgParams()
                    api.getGasRecords(vehicleId, useMpg, useUkMpg).unwrap().map { it.toRow() }
                }
                RecordArea.ODOMETER -> api.getOdometerRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.PLAN -> api.getPlanRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.SUPPLY -> api.getSupplyRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.REMINDER -> api.getReminders(vehicleId).unwrap().map { it.toRow() }
                RecordArea.EQUIPMENT -> api.getEquipmentRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.NOTE -> api.getNotes(vehicleId).unwrap().map { it.toRow() }
            }
        }

    /** Count + most-recent (or soonest-due, for reminders) date for a single area. */
    suspend fun getAreaSummary(area: RecordArea, vehicleId: String): Result<AreaSummary> =
        runCatching {
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
            val parsed = DateFormats.firstNotNullOfOrNull { fmt ->
                runCatching { LocalDate.parse(d, fmt) }.getOrNull()
            } ?: continue
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

    // ---- Records: fetch a single record for editing ----
    suspend fun getRecordForEdit(
        area: RecordArea,
        vehicleId: String,
        id: String,
    ): Result<RecordEditData?> = runCatching {
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
    ): Result<List<FileAttachmentResponse>> = runCatching {
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
    ): Result<Unit> = runCatching {
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
    ): Result<FileAttachment> = runCatching {
        val media = (mimeType ?: "application/octet-stream").toMediaTypeOrNull()
        val part = MultipartBody.Part.createFormData("documents", fileName, bytes.toRequestBody(media))
        val response = api().uploadDocuments(listOf(part))
        if (!response.isSuccessful) throw mapError(response.code(), response.errorBody()?.string())
        val body = response.body()?.string().orEmpty()
        parseUploadedLocation(body, fileName)
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
    suspend fun downloadAttachment(location: String): Result<ByteArray> = runCatching {
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

/** Date layouts LubeLogger may emit depending on locale/culture settings. */
private val DateFormats: List<DateTimeFormatter> = listOf(
    "yyyy-MM-dd", "M/d/yyyy", "MM/dd/yyyy", "yyyy/MM/dd", "dd/MM/yyyy", "dd-MM-yyyy", "d.M.yyyy",
).map { DateTimeFormatter.ofPattern(it) }
