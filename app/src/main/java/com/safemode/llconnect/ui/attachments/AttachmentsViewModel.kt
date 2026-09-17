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

    private fun currentFiles(): List<FileAttachment> =
        (state.value as? UiState.Success)?.data?.map {
            FileAttachment(name = it.name, location = it.location)
        } ?: emptyList()

    fun addUploaded(fileName: String, mimeType: String?, bytes: ByteArray) {
        viewModelScope.launch {
            _busy.value = true
            repository.uploadDocument(fileName, mimeType, bytes).fold(
                onSuccess = { uploaded ->
                    commit(currentFiles() + uploaded, "Attachment added.")
                },
                onFailure = {
                    _message.value = it.message ?: "Upload failed."
                    _busy.value = false
                },
            )
        }
    }

    fun rename(target: FileAttachmentResponse, newName: String) {
        val updated = currentFiles().map {
            if (it.location == target.location && it.name == target.name) it.copy(name = newName) else it
        }
        commitAsync(updated, "Renamed.")
    }

    fun delete(target: FileAttachmentResponse) {
        val updated = currentFiles().filterNot {
            it.location == target.location && it.name == target.name
        }
        commitAsync(updated, "Attachment deleted.")
    }

    private fun commitAsync(files: List<FileAttachment>, successMessage: String) {
        viewModelScope.launch {
            _busy.value = true
            commit(files, successMessage)
        }
    }

    private suspend fun commit(files: List<FileAttachment>, successMessage: String) {
        repository.setAttachments(area, vehicleId, recordId, files).fold(
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
