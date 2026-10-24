package com.safemode.llconnect.data

import com.safemode.llconnect.data.remote.models.FileAttachment
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentMergeTest {

    private val a = FileAttachment("a.jpg", "documents/a.jpg")
    private val b = FileAttachment("b.jpg", "documents/b.jpg")
    private val scan = FileAttachment("receipt.jpg", "documents/receipt.jpg")
    private val elsewhere = FileAttachment("other.pdf", "documents/other.pdf")

    @Test fun scanAddedInFormIsKept() {
        // The regression: a receipt scanned while editing was dropped on save.
        val merged = AttachmentMerge.merge(loaded = listOf(a), edited = listOf(a, scan), current = listOf(a))
        assertEquals(listOf(a, scan), merged)
    }

    @Test fun attachmentAddedElsewhereIsKept() {
        val merged = AttachmentMerge.merge(loaded = listOf(a), edited = listOf(a), current = listOf(a, elsewhere))
        assertEquals(listOf(a, elsewhere), merged)
    }

    @Test fun bothSidesAddingIsKept() {
        val merged = AttachmentMerge.merge(
            loaded = listOf(a),
            edited = listOf(a, scan),
            current = listOf(a, elsewhere),
        )
        assertEquals(listOf(a, elsewhere, scan), merged)
    }

    @Test fun removalInFormIsHonored() {
        val merged = AttachmentMerge.merge(loaded = listOf(a, b), edited = listOf(a), current = listOf(a, b))
        assertEquals(listOf(a), merged)
    }

    @Test fun noChangesKeepsServerList() {
        val merged = AttachmentMerge.merge(loaded = listOf(a, b), edited = listOf(a, b), current = listOf(a, b))
        assertEquals(listOf(a, b), merged)
    }

    @Test fun addedFileAlreadyOnServerIsNotDuplicated() {
        val merged = AttachmentMerge.merge(loaded = emptyList(), edited = listOf(scan), current = listOf(scan))
        assertEquals(listOf(scan), merged)
    }
}
