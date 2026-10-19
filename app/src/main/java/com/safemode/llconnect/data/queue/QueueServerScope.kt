package com.safemode.llconnect.data.queue

/**
 * Whether a queued entry belongs to the currently configured server and may be flushed to it.
 *
 * Entries are tagged with the server they were saved against ([QueuedEntry.serverId]) so changing
 * the host in Settings never replays server A's records onto server B. Entries saved before server
 * tagging existed (blank id) are treated as belonging to the current server, preserving the
 * previous behavior for anything already queued at upgrade time.
 */
fun queuedForCurrentServer(entryServerId: String, currentServerId: String): Boolean =
    entryServerId.isBlank() || currentServerId.isBlank() || entryServerId == currentServerId
