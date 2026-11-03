package com.safemode.llconnect.ui.attachments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.remote.models.FileAttachment
import com.safemode.llconnect.data.remote.models.FileAttachmentResponse
import com.safemode.llconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AttachmentsViewModel(
    val area: RecordArea,
    private val vehicleId: String,
    private val recordId: String,
) : ViewModel() {
    private val repository = Graph.repository

    private val _state = MutableStateFlow<UiState<List<FileAttachmentResponse>>>(UiState.Loading)
    val state: StateFlow<UiState<List<FileAttachmentResponse>>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = repository.getAttachments(area, vehicleId, recordId).fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Failed to load attachments.") },
            )
        }
    }

    /** Uploads [file] (a temporary copy, deleted afterwards) and adds it to the record. */
    fun addUploaded(file: File, fileName: String, mimeType: String?) {
        viewModelScope.launch {
            _busy.value = true
            val uploaded = try {
                repository.uploadDocument(file, fileName, mimeType)
            } finally {
                file.delete()
            }
            uploaded.fold(
                onSuccess = { attachment -> commit("Attachment added.") { it + attachment } },
                onFailure = {
                    _message.value = it.message ?: "Upload failed."
                    _busy.value = false
                },
            )
        }
    }

    fun rename(target: FileAttachmentResponse, newName: String) {
        commitAsync("Renamed.") { files ->
            files.map { if (it.matches(target)) it.copy(name = newName) else it }
        }
    }

    fun delete(target: FileAttachmentResponse) {
        commitAsync("Attachment deleted.") { files -> files.filterNot { it.matches(target) } }
    }

    private fun FileAttachment.matches(target: FileAttachmentResponse) =
        location == target.location && name == target.name

    private fun commitAsync(successMessage: String, edit: (List<FileAttachment>) -> List<FileAttachment>) {
        viewModelScope.launch {
            _busy.value = true
            commit(successMessage, edit)
        }
    }

    // The edit is applied to the record's current list on the server, not the one shown here.
    private suspend fun commit(successMessage: String, edit: (List<FileAttachment>) -> List<FileAttachment>) {
        repository.editAttachments(area, vehicleId, recordId, edit).fold(
            onSuccess = {
                _message.value = successMessage
                _state.value = repository.getAttachments(area, vehicleId, recordId).fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "Failed to reload attachments.") },
                )
                _busy.value = false
            },
            onFailure = {
                _message.value = it.message ?: "Update failed."
                _busy.value = false
            },
        )
    }

    fun consumeMessage() {
        _message.value = null
    }
}
