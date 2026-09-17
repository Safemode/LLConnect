package com.safemode.llconnect.data

import com.safemode.llconnect.data.remote.ApiProvider
import com.safemode.llconnect.data.remote.LubeLoggerApi
import com.safemode.llconnect.data.remote.models.FileAttachment
import com.safemode.llconnect.data.remote.models.FileAttachmentResponse
import com.safemode.llconnect.data.remote.models.GasRecordRequest
import com.safemode.llconnect.data.remote.models.GenericRecordRequest
import com.safemode.llconnect.data.remote.models.OdometerRecordRequest
import com.safemode.llconnect.data.remote.models.TaxRecordRequest
import com.safemode.llconnect.data.remote.models.Vehicle
import com.safemode.llconnect.data.remote.models.VehicleAddRequest
import com.safemode.llconnect.data.remote.models.VehicleUpdateRequest
import com.safemode.llconnect.data.remote.models.WhoAmI
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response

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

    suspend fun addVehicle(body: VehicleAddRequest): Result<Unit> = callUnit { it.addVehicle(body) }

    suspend fun updateVehicle(body: VehicleUpdateRequest): Result<Unit> =
        callUnit { it.updateVehicle(body) }

    suspend fun deleteVehicle(id: String): Result<Unit> = callUnit { it.deleteVehicle(id) }

    // ---- System / user ----
    suspend fun whoAmI(): Result<WhoAmI> = call { it.whoAmI() }

    suspend fun version(): Result<String> =
        call { it.version() }.map { it.string().trim().trim('"') }

    suspend fun serverInfo(): Result<String> = call { it.serverInfo() }.map { it.string() }

    suspend fun makeBackup(): Result<String> = call { it.makeBackup() }.map { it.string() }

    // ---- Records: read ----
    suspend fun getRecords(area: RecordArea, vehicleId: String): Result<List<RecordRow>> =
        runCatching {
            val api = api()
            when (area) {
                RecordArea.SERVICE -> api.getServiceRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.REPAIR -> api.getRepairRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.UPGRADE -> api.getUpgradeRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.TAX -> api.getTaxRecords(vehicleId).unwrap().map { it.toRow(isTax = true) }
                RecordArea.GAS -> api.getGasRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.ODOMETER -> api.getOdometerRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.PLAN -> api.getPlanRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.SUPPLY -> api.getSupplyRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.REMINDER -> api.getReminders(vehicleId).unwrap().map { it.toRow() }
                RecordArea.EQUIPMENT -> api.getEquipmentRecords(vehicleId).unwrap().map { it.toRow() }
                RecordArea.NOTE -> api.getNotes(vehicleId).unwrap().map { it.toRow() }
            }
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
            when (area) {
                RecordArea.SERVICE -> api.addServiceRecord(vehicleId, body)
                RecordArea.REPAIR -> api.addRepairRecord(vehicleId, body)
                RecordArea.UPGRADE -> api.addUpgradeRecord(vehicleId, body)
                else -> throw IllegalArgumentException("Unsupported area for service-like add: $area")
            }
        }

    suspend fun addTax(vehicleId: String, body: TaxRecordRequest): Result<Unit> =
        callUnit { it.addTaxRecord(vehicleId, body) }

    suspend fun addGas(vehicleId: String, body: GasRecordRequest): Result<Unit> =
        callUnit { it.addGasRecord(vehicleId, body) }

    suspend fun addOdometer(vehicleId: String, body: OdometerRecordRequest): Result<Unit> =
        callUnit { it.addOdometerRecord(vehicleId, body) }

    // ---- Records: update (core areas) ----
    suspend fun updateServiceLike(area: RecordArea, body: GenericRecordRequest): Result<Unit> =
        callUnit { api ->
            when (area) {
                RecordArea.SERVICE -> api.updateServiceRecord(body)
                RecordArea.REPAIR -> api.updateRepairRecord(body)
                RecordArea.UPGRADE -> api.updateUpgradeRecord(body)
                else -> throw IllegalArgumentException("Unsupported area for service-like update: $area")
            }
        }

    suspend fun updateTax(body: TaxRecordRequest): Result<Unit> =
        callUnit { it.updateTaxRecord(body) }

    suspend fun updateGas(body: GasRecordRequest): Result<Unit> =
        callUnit { it.updateGasRecord(body) }

    suspend fun updateOdometer(body: OdometerRecordRequest): Result<Unit> =
        callUnit { it.updateOdometerRecord(body) }

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
            else -> null
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
                api.getServiceRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
            )
            RecordArea.REPAIR -> api.updateRepairRecord(
                api.getRepairRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
            )
            RecordArea.UPGRADE -> api.updateUpgradeRecord(
                api.getUpgradeRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
            )
            RecordArea.TAX -> api.updateTaxRecord(
                api.getTaxRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toTaxUpdateRequest(files)
            )
            RecordArea.GAS -> api.updateGasRecord(
                api.getGasRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
            )
            RecordArea.ODOMETER -> api.updateOdometerRecord(
                api.getOdometerRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
            )
            RecordArea.PLAN -> api.updatePlanRecord(
                api.getPlanRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
            )
            RecordArea.SUPPLY -> api.updateSupplyRecord(
                api.getSupplyRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
            )
            RecordArea.EQUIPMENT -> api.updateEquipmentRecord(
                api.getEquipmentRecords(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
            )
            RecordArea.NOTE -> api.updateNote(
                api.getNotes(vehicleId).unwrap().firstOrNull { match(it.id) }.orThrow().toUpdateRequest(files)
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
