package com.safemode.llconnect.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.queue.QueuedEntry
import com.safemode.llconnect.data.remote.ServerReachability
import com.safemode.llconnect.ui.common.EmptyState
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val flushing by viewModel.flushing.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val distanceUnit by Graph.settingsRepository.config
        .map { it.distanceUnit.suffix }
        .collectAsStateWithLifecycle(initialValue = Graph.apiProvider.currentConfig().distanceUnit.suffix)

    var pendingDelete by remember { mutableStateOf<QueuedEntry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CloudDone,
                title = "Nothing queued",
                message = "Records you save while the server is unreachable wait here, then upload " +
                    "on their own once it's back.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            QueueHeader(
                count = entries.size,
                serverState = serverState,
                flushing = flushing,
                onRetry = viewModel::retryNow,
                onClear = { confirmClear = true },
            )
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    QueuedEntryCard(
                        entry = entry,
                        distanceUnit = distanceUnit,
                        onDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Discard queued entry?") },
            text = { Text("This removes the entry and its scanned attachments without uploading them. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(entry.id)
                    pendingDelete = null
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the queue?") },
            text = { Text("This removes every queued entry and its attachments without uploading them. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun QueueHeader(
    count: Int,
    serverState: ServerReachability,
    flushing: Boolean,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    val hint = when {
        flushing -> "Uploading…"
        serverState == ServerReachability.REACHABLE -> "Server reachable"
        serverState == ServerReachability.CHECKING -> "Checking server…"
        else -> "Server unreachable — will upload when it's back"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$count waiting to upload",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClear) { Text("Clear all") }
        Button(onClick = onRetry, enabled = !flushing) {
            if (flushing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text("Retry now")
        }
    }
}

@Composable
private fun QueuedEntryCard(
    entry: QueuedEntry,
    distanceUnit: String,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.area.label.trimEnd('s')} · ${entry.vehicleName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val summary = summaryOf(entry, distanceUnit)
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val attachmentCount = entry.pendingAttachments.size + entry.uploadedAttachments.size
                val meta = buildList {
                    add("Queued ${relativeTime(entry.createdAt)}")
                    if (attachmentCount > 0) {
                        add("$attachmentCount attachment${if (attachmentCount == 1) "" else "s"}")
                    }
                }.joinToString("  ·  ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                entry.lastError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Discard entry",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Best-effort one-line summary from whichever identifying fields the entry carries. */
private fun summaryOf(entry: QueuedEntry, distanceUnit: String): String {
    val f = entry.fields
    val parts = mutableListOf<String>()
    (f.description?.takeIf { it.isNotBlank() } ?: f.noteText?.takeIf { it.isNotBlank() })
        ?.let { parts += it }
    f.odometer?.let { parts += "%,d %s".format(it, distanceUnit) }
    f.cost?.let { parts += "$%,.2f".format(it) }
    f.date.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString("  ·  ")
}

private fun relativeTime(createdAt: Long): String {
    val minutes = (System.currentTimeMillis() - createdAt) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}
