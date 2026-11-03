package com.safemode.llconnect.ui.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReloadJobTest {

    @Test
    fun refreshSpinnerStaysUpUntilTheLoadFinishes() = runBlocking {
        val reloads = ReloadJob()
        val gate = CompletableDeferred<Unit>()
        reloads.launch(this, refresh = true) { gate.await() }
        yield()
        assertTrue(reloads.refreshing.value)

        gate.complete(Unit)
        yield()
        assertFalse(reloads.refreshing.value)
    }

    @Test
    fun cancelledRefreshDoesNotHideTheSpinnerOfItsReplacement() = runBlocking {
        val reloads = ReloadJob()
        val second = CompletableDeferred<Unit>()
        reloads.launch(this, refresh = true) { CompletableDeferred<Unit>().await() }
        yield()
        reloads.launch(this, refresh = true) { second.await() }
        yield()
        yield()
        assertTrue(reloads.refreshing.value)

        second.complete(Unit)
        yield()
        assertFalse(reloads.refreshing.value)
    }

    @Test
    fun silentReloadThatReplacesARefreshKeepsTheSpinnerUp() = runBlocking {
        val reloads = ReloadJob()
        val silent = CompletableDeferred<Unit>()
        reloads.launch(this, refresh = true) { CompletableDeferred<Unit>().await() }
        yield()
        reloads.launch(this) { silent.await() }
        yield()
        yield()
        assertTrue(reloads.refreshing.value)

        silent.complete(Unit)
        yield()
        assertFalse(reloads.refreshing.value)
    }

    @Test
    fun silentReloadNeverShowsTheSpinner() = runBlocking {
        val reloads = ReloadJob()
        val gate = CompletableDeferred<Unit>()
        reloads.launch(this) { gate.await() }
        yield()
        gate.complete(Unit)
        yield()
        assertFalse(reloads.refreshing.value)
    }

    @Test
    fun replacedLoadStopsBeforeWritingState() = runBlocking {
        val reloads = ReloadJob()
        val writes = mutableListOf<String>()
        val first = CompletableDeferred<Unit>()
        reloads.launch(this) {
            first.await()
            writes += "stale"
        }
        yield()
        reloads.launch(this) { writes += "fresh" }
        first.complete(Unit)
        yield()
        yield()
        assertEquals(listOf("fresh"), writes)
    }
}
