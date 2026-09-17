package com.safemode.llconnect.data.remote

import com.safemode.llconnect.data.remote.models.EquipmentRecord
import com.safemode.llconnect.data.remote.models.EquipmentRecordRequest
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
import com.safemode.llconnect.data.remote.models.ReminderRecordRequest
import com.safemode.llconnect.data.remote.models.SupplyRecord
import com.safemode.llconnect.data.remote.models.SupplyRecordRequest
import com.safemode.llconnect.data.remote.models.TaxRecordRequest
import com.safemode.llconnect.data.remote.models.Vehicle
import com.safemode.llconnect.data.remote.models.VehicleAddRequest
import com.safemode.llconnect.data.remote.models.VehicleUpdateRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Retrofit interface for the LubeLogger REST API (v1.7.3).
 * Auth headers (x-api-key / Basic) and the culture-invariant header are added
 * by [AuthInterceptor], so they are not declared here.
 */
interface LubeLoggerApi {

    // ---- Vehicles ----
    @GET("api/vehicles")
    suspend fun getVehicles(): Response<List<Vehicle>>

    @GET("api/vehicle/info")
    suspend fun getVehicleInfo(@Query("vehicleId") vehicleId: String? = null): Response<List<Vehicle>>

    @GET("api/vehicle/adjustedodometer")
    suspend fun getAdjustedOdometer(
        @Query("vehicleId") vehicleId: String,
        @Query("odometer") odometer: String,
    ): Response<ResponseBody>

    @POST("api/vehicles/add")
    suspend fun addVehicle(@Body body: VehicleAddRequest): Response<ResponseBody>

    @PUT("api/vehicles/update")
    suspend fun updateVehicle(@Body body: VehicleUpdateRequest): Response<ResponseBody>

    @DELETE("api/vehicles/delete")
    suspend fun deleteVehicle(@Query("id") id: String): Response<ResponseBody>

    // ---- Odometer Records ----
    @GET("api/vehicle/odometerrecords")
    suspend fun getOdometerRecords(@Query("vehicleId") vehicleId: String): Response<List<OdometerRecord>>

    @GET("api/vehicle/odometerrecords/latest")
    suspend fun getLatestOdometer(@Query("vehicleId") vehicleId: String): Response<OdometerRecord>

    @POST("api/vehicle/odometerrecords/add")
    suspend fun addOdometerRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: OdometerRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/odometerrecords/update")
    suspend fun updateOdometerRecord(@Body body: OdometerRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/odometerrecords/delete")
    suspend fun deleteOdometerRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Service Records ----
    @GET("api/vehicle/servicerecords")
    suspend fun getServiceRecords(@Query("vehicleId") vehicleId: String): Response<List<GenericRecord>>

    @POST("api/vehicle/servicerecords/add")
    suspend fun addServiceRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: GenericRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/servicerecords/update")
    suspend fun updateServiceRecord(@Body body: GenericRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/servicerecords/delete")
    suspend fun deleteServiceRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Repair Records ----
    @GET("api/vehicle/repairrecords")
    suspend fun getRepairRecords(@Query("vehicleId") vehicleId: String): Response<List<GenericRecord>>

    @POST("api/vehicle/repairrecords/add")
    suspend fun addRepairRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: GenericRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/repairrecords/update")
    suspend fun updateRepairRecord(@Body body: GenericRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/repairrecords/delete")
    suspend fun deleteRepairRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Upgrade Records ----
    @GET("api/vehicle/upgraderecords")
    suspend fun getUpgradeRecords(@Query("vehicleId") vehicleId: String): Response<List<GenericRecord>>

    @POST("api/vehicle/upgraderecords/add")
    suspend fun addUpgradeRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: GenericRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/upgraderecords/update")
    suspend fun updateUpgradeRecord(@Body body: GenericRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/upgraderecords/delete")
    suspend fun deleteUpgradeRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Tax Records ----
    @GET("api/vehicle/taxrecords")
    suspend fun getTaxRecords(@Query("vehicleId") vehicleId: String): Response<List<GenericRecord>>

    @POST("api/vehicle/taxrecords/add")
    suspend fun addTaxRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: TaxRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/taxrecords/update")
    suspend fun updateTaxRecord(@Body body: TaxRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/taxrecords/delete")
    suspend fun deleteTaxRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Gas Records ----
    @GET("api/vehicle/gasrecords")
    suspend fun getGasRecords(
        @Query("vehicleId") vehicleId: String,
        @Query("useMPG") useMPG: String? = null,
        @Query("useUKMPG") useUKMPG: String? = null,
    ): Response<List<GasRecord>>

    @POST("api/vehicle/gasrecords/add")
    suspend fun addGasRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: GasRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/gasrecords/update")
    suspend fun updateGasRecord(@Body body: GasRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/gasrecords/delete")
    suspend fun deleteGasRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Plan Records ----
    @GET("api/vehicle/planrecords")
    suspend fun getPlanRecords(@Query("vehicleId") vehicleId: String): Response<List<PlanRecord>>

    @POST("api/vehicle/planrecords/add")
    suspend fun addPlanRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: PlanRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/planrecords/update")
    suspend fun updatePlanRecord(@Body body: PlanRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/planrecords/delete")
    suspend fun deletePlanRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Supply Records ----
    @GET("api/vehicle/supplyrecords")
    suspend fun getSupplyRecords(@Query("vehicleId") vehicleId: String): Response<List<SupplyRecord>>

    @POST("api/vehicle/supplyrecords/add")
    suspend fun addSupplyRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: SupplyRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/supplyrecords/update")
    suspend fun updateSupplyRecord(@Body body: SupplyRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/supplyrecords/delete")
    suspend fun deleteSupplyRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Reminders ----
    @GET("api/vehicle/reminders")
    suspend fun getReminders(@Query("vehicleId") vehicleId: String): Response<List<ReminderRecord>>

    @POST("api/vehicle/reminders/add")
    suspend fun addReminder(
        @Query("vehicleId") vehicleId: String,
        @Body body: ReminderRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/reminders/update")
    suspend fun updateReminder(@Body body: ReminderRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/reminders/delete")
    suspend fun deleteReminder(@Query("id") id: String): Response<ResponseBody>

    // ---- Equipment ----
    @GET("api/vehicle/equipmentrecords")
    suspend fun getEquipmentRecords(@Query("vehicleId") vehicleId: String): Response<List<EquipmentRecord>>

    @POST("api/vehicle/equipmentrecords/add")
    suspend fun addEquipmentRecord(
        @Query("vehicleId") vehicleId: String,
        @Body body: EquipmentRecordRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/equipmentrecords/update")
    suspend fun updateEquipmentRecord(@Body body: EquipmentRecordRequest): Response<ResponseBody>

    @DELETE("api/vehicle/equipmentrecords/delete")
    suspend fun deleteEquipmentRecord(@Query("id") id: String): Response<ResponseBody>

    // ---- Notes ----
    @GET("api/vehicle/notes")
    suspend fun getNotes(@Query("vehicleId") vehicleId: String): Response<List<Note>>

    @POST("api/vehicle/notes/add")
    suspend fun addNote(
        @Query("vehicleId") vehicleId: String,
        @Body body: NoteRequest,
    ): Response<ResponseBody>

    @PUT("api/vehicle/notes/update")
    suspend fun updateNote(@Body body: NoteRequest): Response<ResponseBody>

    @DELETE("api/vehicle/notes/delete")
    suspend fun deleteNote(@Query("id") id: String): Response<ResponseBody>

    // ---- Cross-vehicle ("all") reads ----
    @GET("api/vehicle/servicerecords/all")
    suspend fun getAllServiceRecords(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Response<List<GenericRecord>>

    @GET("api/vehicle/repairrecords/all")
    suspend fun getAllRepairRecords(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Response<List<GenericRecord>>

    @GET("api/vehicle/upgraderecords/all")
    suspend fun getAllUpgradeRecords(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Response<List<GenericRecord>>

    @GET("api/vehicle/taxrecords/all")
    suspend fun getAllTaxRecords(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Response<List<GenericRecord>>

    @GET("api/vehicle/gasrecords/all")
    suspend fun getAllGasRecords(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("useMPG") useMPG: String? = null,
        @Query("useUKMPG") useUKMPG: String? = null,
    ): Response<List<GasRecord>>

    @GET("api/vehicle/odometerrecords/all")
    suspend fun getAllOdometerRecords(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Response<List<OdometerRecord>>

    @GET("api/vehicle/reminders/all")
    suspend fun getAllReminders(): Response<List<ReminderRecord>>

    // ---- Tools ----
    @GET("api/cleanup")
    suspend fun cleanup(@Query("deepClean") deepClean: String? = null): Response<ResponseBody>

    @GET("api/tempfiles")
    suspend fun tempFiles(): Response<ResponseBody>

    @GET("api/vehicle/reminders/send")
    suspend fun sendReminders(): Response<ResponseBody>

    // ---- System / User ----
    // Returned as a raw body and parsed case-insensitively in the repository, because
    // the instance may serialize this object as camelCase or PascalCase.
    @GET("api/whoami")
    suspend fun whoAmI(): Response<ResponseBody>

    @GET("api/info")
    suspend fun serverInfo(): Response<ResponseBody>

    @GET("api/version")
    suspend fun version(): Response<ResponseBody>

    @Multipart
    @POST("api/documents/upload")
    suspend fun uploadDocuments(@Part parts: List<MultipartBody.Part>): Response<ResponseBody>

    @GET("api/extrafields")
    suspend fun extraFields(): Response<ResponseBody>

    @GET("api/calendar")
    suspend fun calendar(): Response<ResponseBody>

    @GET("api/makebackup")
    suspend fun makeBackup(): Response<ResponseBody>
}
