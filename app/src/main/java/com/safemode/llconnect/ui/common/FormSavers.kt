package com.safemode.llconnect.ui.common

import androidx.compose.runtime.saveable.Saver
import com.safemode.llconnect.data.queue.QueuedAttachment
import com.safemode.llconnect.data.remote.models.ExtraField
import com.safemode.llconnect.data.remote.models.FileAttachment

/**
 * Savers for the non-primitive lists a form keeps in rememberSaveable, so they survive rotation,
 * a theme change, and the process being killed in the background. Each list is flattened to a
 * list of strings, [width] values per item.
 */
private fun <T> flatListSaver(
    width: Int,
    write: (T) -> List<String?>,
    read: (List<String?>) -> T,
): Saver<List<T>, ArrayList<String?>> = Saver(
    save = { list -> ArrayList(list.flatMap(write)) },
    restore = { flat -> flat.chunked(width).map(read) },
)

val FileAttachmentListSaver = flatListSaver<FileAttachment>(
    width = 2,
    write = { listOf(it.name, it.location) },
    read = { FileAttachment(name = it[0], location = it[1]) },
)

private val ExtraFieldListSaver = flatListSaver<ExtraField>(
    width = 2,
    write = { listOf(it.name, it.value) },
    read = { ExtraField(name = it[0], value = it[1]) },
)

/** Extra fields stay null until loaded (null means "not loaded", not "none"). */
val NullableExtraFieldListSaver = Saver<List<ExtraField>?, ArrayList<String?>>(
    save = { list -> list?.let { with(ExtraFieldListSaver) { save(it) } } },
    restore = { flat -> ExtraFieldListSaver.restore(flat) },
)

val QueuedAttachmentListSaver = flatListSaver<QueuedAttachment>(
    width = 4,
    write = { listOf(it.fileName, it.mimeType, it.localFileName, it.location) },
    read = {
        QueuedAttachment(
            fileName = it[0].orEmpty(),
            mimeType = it[1].orEmpty(),
            localFileName = it[2],
            location = it[3],
        )
    },
)
