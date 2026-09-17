package com.safemode.llconnect.ui.attachments

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.remote.models.FileAttachmentResponse
import com.safemode.llconnect.ui.common.EmptyState
import com.safemode.llconnect.ui.common.ErrorState
import com.safemode.llconnect.ui.common.LoadingState
import com.safemode.llconnect.ui.common.UiState
import com.safemode.llconnect.ui.records.recordAreaFromName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val AcceptedMimeTypes = arrayOf(
    "image/png",
    "image/jpeg",
    "application/pdf",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

@Suppress("UNCHECKED_CAST")
private fun attachmentsFactory(vehicleId: String, areaName: String, recordId: String) =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AttachmentsViewModel(recordAreaFromName(areaName), vehicleId, recordId) as T
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsScreen(
    vehicleId: String,
    areaName: String,
    recordId: String,
    onBack: () -> Unit,
) {
    val viewModel: AttachmentsViewModel = viewModel(
        key = "attachments-$vehicleId-$areaName-$recordId",
        factory = attachmentsFactory(vehicleId, areaName, recordId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var renameTarget by remember { mutableStateOf<FileAttachmentResponse?>(null) }
    var deleteTarget by remember { mutableStateOf<FileAttachmentResponse?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { readFile(context, uri) }
                if (loaded != null) {
                    viewModel.addUploaded(loaded.name, loaded.mime, loaded.bytes)
                } else {
                    snackbarHostState.showSnackbar("Could not read the selected file.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attachments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state is UiState.Success && !busy) {
                ExtendedFloatingActionButton(
                    text = { Text("Add") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { picker.launch(AcceptedMimeTypes) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> ErrorState(message = "Not connected.")
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.AttachFile,
                        title = "No attachments",
                        message = "Tap Add to upload a photo, PDF, or document.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(s.data, key = { (it.location ?: "") + (it.name ?: "") }) { file ->
                            AttachmentCard(
                                file = file,
                                onOpen = {
                                    scope.launch {
                                        openAttachment(context, viewModel, file, snackbarHostState)
                                    }
                                },
                                onRename = { renameTarget = file },
                                onDelete = { deleteTarget = file },
                            )
                        }
                    }
                }
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    renameTarget?.let { target ->
        var newName by remember(target) { mutableStateOf(target.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename attachment") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        viewModel.rename(target, newName.trim())
                        renameTarget = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete attachment?") },
            text = { Text("Remove \"${target.name ?: "this file"}\" from the record?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AttachmentCard(
    file: FileAttachmentResponse,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = file.name?.ifBlank { "(unnamed)" } ?: "(unnamed)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (file.isPending == true) {
                    AssistChip(onClick = {}, label = { Text("Pending") })
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private data class LoadedFile(val name: String, val mime: String?, val bytes: ByteArray)

private fun readFile(context: Context, uri: Uri): LoadedFile? = try {
    val resolver = context.contentResolver
    var name = "upload"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) c.getString(idx)?.let { name = it }
        }
    }
    val mime = resolver.getType(uri)
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    LoadedFile(name, mime, bytes)
} catch (e: Exception) {
    null
}

/** Downloads the attachment to the cache and opens it with an external viewer. */
private suspend fun openAttachment(
    context: Context,
    viewModel: AttachmentsViewModel,
    file: FileAttachmentResponse,
    snackbar: SnackbarHostState,
) {
    val location = file.location
    if (location.isNullOrBlank()) {
        snackbar.showSnackbar("This attachment has no location yet.")
        return
    }
    snackbar.showSnackbar("Opening…")
    val result = Graph.repository.downloadAttachment(location)
    result.fold(
        onSuccess = { bytes ->
            try {
                val name = file.name?.substringAfterLast('/')?.ifBlank { null }
                    ?: location.substringAfterLast('/')
                val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
                val outFile = File(dir, name)
                withContext(Dispatchers.IO) { outFile.writeBytes(bytes) }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile,
                )
                val ext = MimeTypeMap.getFileExtensionFromUrl(name).lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open with"))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No app can open this file type.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Could not open the file.")
            }
        },
        onFailure = { snackbar.showSnackbar(it.message ?: "Download failed.") },
    )
}
