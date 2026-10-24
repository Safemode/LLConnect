package com.safemode.llconnect.data

import com.safemode.llconnect.data.remote.models.FileAttachment

/**
 * Builds the attachment list for an edit save. LubeLogger replaces the whole record on update, so
 * the form can't just send the list it loaded (that would wipe attachments added elsewhere in the
 * meantime), and it can't just send the server's current list either (that would drop receipts
 * scanned or removed in the form). Instead this applies the form's own changes, relative to what
 * it loaded, onto the server's current list.
 */
object AttachmentMerge {

    /**
     * @param loaded the attachments the form loaded when it opened.
     * @param edited the attachments in the form at save time (loaded, plus scans, minus removals).
     * @param current the attachments the server holds right before the save.
     */
    fun merge(
        loaded: List<FileAttachment>,
        edited: List<FileAttachment>,
        current: List<FileAttachment>,
    ): List<FileAttachment> {
        val loadedKeys = loaded.map(::key).toSet()
        val editedKeys = edited.map(::key).toSet()
        val removed = loadedKeys - editedKeys
        val added = edited.filter { key(it) !in loadedKeys }

        val kept = current.filterNot { key(it) in removed }
        val keptKeys = kept.map(::key).toSet()
        return kept + added.filterNot { key(it) in keptKeys }
    }

    /** Attachments are identified by their server location, falling back to the name. */
    private fun key(file: FileAttachment): String = file.location ?: file.name.orEmpty()
}
