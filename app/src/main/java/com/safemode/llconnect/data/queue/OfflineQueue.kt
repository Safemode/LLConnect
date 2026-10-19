package com.safemode.llconnect.data.queue

import com.safemode.llconnect.data.LubeLoggerRepository
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.remote.ServerStatus
import com.safemode.llconnect.data.remote.models.FileAttachment
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Preserves new records (and their scanned attachments) the user saved while the server was
 * unreachable, and flushes them once it's back — checking each entry against the server first so
 * it isn't created twice.
 *
 * State lives on disk under `filesDir/queue/`: the entry list in `queue.json`, each entry's local
 * attachment images under `files/<entryId>/`, and a `staging/` area for images a form has scanned
 * but not yet committed. Lightweight on purpose (a JSON file + a mutex), matching the app's
 * manual-DI / file-based style rather than pulling in a database.
 */
class OfflineQueue(
    filesDir: File,
    private val serverStatus: ServerStatus,
    private val repository: LubeLoggerRepository,
    private val currentServerId: () -> String,
    scope: CoroutineScope,
) {
    private val root = File(filesDir, "queue")
    private val filesRoot = File(root, "files")
    private val stagingDir = File(root, "staging")
    private val jsonFile = File(root, "queue.json")

    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<QueuedEntry>>(Types.newParameterizedType(List::class.java, QueuedEntry::class.java))

    /** Serializes disk writes + in-memory state changes. */
    private val mutex = Mutex()

    /** Ensures only one flush runs at a time (a status flip during flush must not start another). */
    private val flushMutex = Mutex()

    private val _entries = MutableStateFlow<List<QueuedEntry>>(emptyList())
    /** Queued entries, oldest first. */
    val entries: StateFlow<List<QueuedEntry>> = _entries.asStateFlow()

    private val _flushing = MutableStateFlow(false)
    /** True while a flush is in progress (drives the "Retry now" spinner). */
    val flushing: StateFlow<Boolean> = _flushing.asStateFlow()

    init {
        listOf(root, filesRoot, stagingDir).forEach { runCatching { it.mkdirs() } }
        scope.launch {
            mutex.withLock { _entries.value = withContext(Dispatchers.IO) { readFromDisk() } }
            // Drop any images a previous session scanned into a form but never committed.
            withContext(Dispatchers.IO) { stagingDir.listFiles()?.forEach { it.delete() } }
        }
    }

    // ---- Staging (scanned images not yet committed to an entry) ----

    /** Writes a scanned image to the staging area so it survives until the form is saved. */
    suspend fun stageImage(bytes: ByteArray, fileName: String, mimeType: String): QueuedAttachment =
        withContext(Dispatchers.IO) {
            val localName = "${UUID.randomUUID()}_$fileName"
            File(stagingDir, localName).writeBytes(bytes)
            QueuedAttachment(fileName = fileName, mimeType = mimeType, localFileName = localName)
        }

    /**
     * Stages an already-written image file (moving it into the staging area) so it survives until
     * the form is saved, without ever holding the whole image in memory. Preferred over
     * [stageImage] for full-resolution scans.
     */
    suspend fun stageImageFile(source: File, fileName: String, mimeType: String): QueuedAttachment =
        withContext(Dispatchers.IO) {
            val localName = "${UUID.randomUUID()}_$fileName"
            val dest = File(stagingDir, localName)
            if (!source.renameTo(dest)) {
                source.copyTo(dest, overwrite = true)
                source.delete()
            }
            QueuedAttachment(fileName = fileName, mimeType = mimeType, localFileName = localName)
        }

    /** Discards a staged image the user removed before saving. */
    suspend fun discardStaged(attachment: QueuedAttachment) = withContext(Dispatchers.IO) {
        attachment.localFileName?.let { File(stagingDir, it).delete() }
        Unit
    }

    // ---- Enqueue / remove ----

    /**
     * Commits a new entry: moves each staged image into the entry's directory and persists. Then
     * attempts a flush (a no-op if the server is still unreachable).
     */
    suspend fun enqueue(
        area: RecordArea,
        vehicleId: String,
        vehicleName: String,
        fields: QueuedFields,
        uploaded: List<QueuedAttachment>,
        staged: List<QueuedAttachment>,
    ) {
        val id = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) {
            val entryDir = File(filesRoot, id).apply { mkdirs() }
            staged.forEach { att ->
                val name = att.localFileName ?: return@forEach
                runCatching {
                    File(stagingDir, name).copyTo(File(entryDir, name), overwrite = true)
                    File(stagingDir, name).delete()
                }
            }
        }
        val entry = QueuedEntry(
            id = id,
            serverId = currentServerId(),
            createdAt = System.currentTimeMillis(),
            vehicleId = vehicleId,
            vehicleName = vehicleName,
            area = area,
            fields = fields,
            uploadedAttachments = uploaded,
            pendingAttachments = staged,
        )
        update { it + entry }
        flush()
    }

    /** Removes one entry and deletes its local attachment files. */
    suspend fun remove(id: String) {
        update { list -> list.filterNot { it.id == id } }
        withContext(Dispatchers.IO) { File(filesRoot, id).deleteRecursively() }
    }

    /** Removes every entry and its files. */
    suspend fun clear() {
        val ids = _entries.value.map { it.id }
        update { emptyList() }
        withContext(Dispatchers.IO) { ids.forEach { File(filesRoot, it).deleteRecursively() } }
    }

    // ---- Flush ----

    /**
     * Submits queued entries oldest-first. No-op when empty, when the server is unreachable, or
     * when a flush is already running. Stops (leaving the rest queued) on the first connectivity
     * failure; records a message on an entry that fails for any other reason and moves on.
     */
    suspend fun flush(): FlushOutcome {
        if (_entries.value.isEmpty() || !serverStatus.isReachable()) return FlushOutcome()
        if (!flushMutex.tryLock()) return FlushOutcome()
        var uploaded = 0
        var failed = 0
        try {
            _flushing.value = true
            val currentServer = currentServerId()
            for (snapshot in _entries.value) {
                if (!serverStatus.isReachable()) break
                // Leave entries saved against a different server queued - never replay them onto
                // whatever host is configured now.
                if (!queuedForCurrentServer(snapshot.serverId, currentServer)) continue
                when (val result = flushOne(snapshot.id)) {
                    FlushResult.Done -> {
                        remove(snapshot.id)
                        uploaded++
                    }
                    FlushResult.Duplicate -> remove(snapshot.id)
                    FlushResult.Connectivity -> break
                    is FlushResult.Failed -> {
                        setError(snapshot.id, result.message)
                        failed++
                    }
                }
            }
        } finally {
            _flushing.value = false
            flushMutex.unlock()
        }
        return FlushOutcome(uploaded, failed)
    }

    /** Result of a flush pass: how many entries were newly created, and how many failed. */
    data class FlushOutcome(val uploaded: Int = 0, val failed: Int = 0)

    private sealed interface FlushResult {
        /** Successfully created on the server. */
        data object Done : FlushResult
        /** Already present on the server — nothing to do. */
        data object Duplicate : FlushResult
        /** Lost the server mid-flush; stop and retry the whole queue later. */
        data object Connectivity : FlushResult
        /** Failed for a non-connectivity reason (e.g. validation); surface and keep queued. */
        data class Failed(val message: String) : FlushResult
    }

    private suspend fun flushOne(entryId: String): FlushResult {
        var entry = _entries.value.firstOrNull { it.id == entryId } ?: return FlushResult.Done

        // 1. Skip if the server already has it (a retry, or a prior write that partially succeeded).
        //    If we can't confirm either way, do NOT create - a connection drop after a write reaches
        //    the server would otherwise make a duplicate on the next flush.
        val duplicate = repository.findDuplicateAdd(entry.area, entry.vehicleId, entry.fields)
        duplicate.onSuccess { if (it) return FlushResult.Duplicate }
        if (duplicate.isFailure) {
            return if (!serverStatus.isReachable()) FlushResult.Connectivity
            else FlushResult.Failed(
                duplicate.exceptionOrNull()?.message ?: "Couldn't verify against the server.",
            )
        }

        // 2. Upload pending attachments, persisting each success so a retry never re-uploads.
        while (entry.pendingAttachments.isNotEmpty()) {
            val att = entry.pendingAttachments.first()
            val file = File(File(filesRoot, entry.id), att.localFileName.orEmpty())
            if (!withContext(Dispatchers.IO) { file.exists() }) {
                // The local image is gone; drop it rather than blocking the entry forever.
                entry = entry.copy(pendingAttachments = entry.pendingAttachments.drop(1))
                replace(entry)
                continue
            }
            val uploadedAtt = repository.uploadDocument(file, att.fileName, att.mimeType).fold(
                onSuccess = { QueuedAttachment(it.name ?: att.fileName, att.mimeType, location = it.location) },
                onFailure = {
                    return if (!serverStatus.isReachable()) FlushResult.Connectivity
                    else FlushResult.Failed(it.message ?: "Attachment upload failed.")
                },
            )
            entry = entry.copy(
                pendingAttachments = entry.pendingAttachments.drop(1),
                uploadedAttachments = entry.uploadedAttachments + uploadedAtt,
            )
            replace(entry)
        }

        // 3. Create the record with all (now server-side) attachments.
        val files = entry.uploadedAttachments.mapNotNull { a ->
            a.location?.let { FileAttachment(name = a.fileName, location = it) }
        }
        return repository.add(entry.area, entry.vehicleId, entry.fields, files).fold(
            onSuccess = { FlushResult.Done },
            onFailure = {
                if (!serverStatus.isReachable()) FlushResult.Connectivity
                else FlushResult.Failed(it.message ?: "Submit failed.")
            },
        )
    }

    // ---- Internal state + disk ----

    private suspend fun replace(entry: QueuedEntry) =
        update { list -> list.map { if (it.id == entry.id) entry else it } }

    private suspend fun setError(id: String, message: String) =
        update { list -> list.map { if (it.id == id) it.copy(lastError = message) else it } }

    private suspend fun update(transform: (List<QueuedEntry>) -> List<QueuedEntry>) = mutex.withLock {
        val next = transform(_entries.value)
        _entries.value = next
        withContext(Dispatchers.IO) { writeToDisk(next) }
    }

    private fun readFromDisk(): List<QueuedEntry> = runCatching {
        if (!jsonFile.exists()) emptyList() else adapter.fromJson(jsonFile.readText()) ?: emptyList()
    }.getOrDefault(emptyList())

    /** Atomic write via a temp file + rename, so a crash mid-write can't corrupt the queue. */
    private fun writeToDisk(list: List<QueuedEntry>) {
        runCatching {
            val tmp = File(root, "queue.json.tmp")
            tmp.writeText(adapter.toJson(list))
            if (!tmp.renameTo(jsonFile)) {
                jsonFile.writeText(adapter.toJson(list))
                tmp.delete()
            }
        }
    }
}
