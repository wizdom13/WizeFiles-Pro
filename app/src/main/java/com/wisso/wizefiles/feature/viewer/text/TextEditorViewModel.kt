// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

import android.content.Context
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.provider.common.readAllBytes
import com.wisso.wizefiles.provider.common.size
import com.wisso.wizefiles.util.ActionState
import com.wisso.wizefiles.util.DataState
import com.wisso.wizefiles.util.isReady
import com.wisso.wizefiles.util.toError
import com.wisso.wizefiles.util.toLoading
import java.io.IOException
import java.nio.charset.StandardCharsets
import android.os.Environment
import com.wisso.wizefiles.provider.os.isLinuxPath
import java.util.concurrent.atomic.AtomicBoolean

class TextEditorViewModel(file: Path) : ViewModel() {
    private val _file = MutableStateFlow(file)
    val file = _file.asStateFlow()

    private val _bytesState = MutableStateFlow<DataState<ByteArray>>(DataState.Loading())

    private var loadJob: Job? = null
    private var reloadJob: Job? = null

    init {
        viewModelScope.launch {
            _file.collectLatest {
                loadJob?.cancel()?.also { loadJob = null }
                reloadJob?.cancel()?.also { reloadJob = null }
                loadJob = launch {
                    mapFileToBytesState(it)
                    if (isActive) {
                        loadJob = null
                    }
                }
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            loadJob?.cancel()?.also { loadJob = null }
            reloadJob?.cancel()?.also { reloadJob = null }
            reloadJob = launch {
                mapFileToBytesState(_file.value)
                if (isActive) {
                    reloadJob = null
                }
            }
        }
    }

    private suspend fun mapFileToBytesState(file: Path) {
        _bytesState.value = _bytesState.value.toLoading()
        try {
            val bytes = runInterruptible(Dispatchers.IO) {
                val size = file.size()
                if (size > MAX_FILE_SIZE) {
                    throw IOException("File size $size is too large")
                }
                file.readAllBytes()
            }
            writeAllowed = runInterruptible(Dispatchers.IO) { file.canWriteInPlace() }
            currentCoroutineContext().ensureActive()
            _bytesState.value = DataState.Success(bytes)
        } catch (e: CancellationException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        } catch (e: Exception) {
            _bytesState.value = _bytesState.value.toError(e)
        }
    }

    val encoding = MutableStateFlow(StandardCharsets.UTF_8)

    private val _textState = MutableStateFlow<DataState<String>>(DataState.Loading())
    val textState = _textState.asStateFlow()

    init {
        viewModelScope.launch {
            _bytesState.combine(encoding) { bytesState, encoding -> bytesState to encoding }
                .collectLatest { (bytesState, encoding) ->
                    when (bytesState) {
                        is DataState.Loading -> _textState.value = _textState.value.toLoading()
                        is DataState.Success -> {
                            _textState.value = _textState.value.toLoading()
                            try {
                                val text = withContext(Dispatchers.Default) {
                                    String(bytesState.data, encoding)
                                }
                                currentCoroutineContext().ensureActive()
                                _textState.value = DataState.Success(text)
                            } catch (e: CancellationException) {
                                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                            } catch (e: Exception) {
                                _textState.value = _textState.value.toError(e)
                            }
                        }
                        is DataState.Error ->
                            _textState.value = _textState.value.toError(bytesState.throwable)
                    }
                }
        }
    }

    private var editorState = TextEditorSaveState()

    private val _isTextChanged = MutableStateFlow(false)
    val isTextChanged = _isTextChanged.asStateFlow()

    private val _canSave = MutableStateFlow(false)
    val canSave = _canSave.asStateFlow()
    private var writeAllowed = false

    fun onTextLoaded(text: String) {
        updateEditorState(editorState.load(text))
    }

    fun onTextChanged(text: String) {
        updateEditorState(editorState.edit(text))
    }

    fun discardTextChanges() {
        updateEditorState(editorState.discardChanges())
    }

    private fun updateEditorState(state: TextEditorSaveState) {
        editorState = state
        _isTextChanged.value = state.hasUnsavedChanges
        _canSave.value = state.canSave
    }

    private val _writeFileState =
        MutableStateFlow<ActionState<Pair<Path, String>, Unit>>(ActionState.Ready())
    val writeFileState = _writeFileState.asStateFlow()

    private val _writeFileResults = MutableSharedFlow<TextEditorWriteResult>(extraBufferCapacity = 1)
    internal val writeFileResults = _writeFileResults.asSharedFlow()
    private val writeRequestPending = AtomicBoolean()

    fun writeFile(path: Path, text: String, context: Context) {
        if (!writeRequestPending.compareAndSet(false, true)) return
        viewModelScope.launch {
            var saveStarted = false
            try {
                writeAllowed = runInterruptible(Dispatchers.IO) { path.canWriteInPlace() }
                when (
                    decideTextEditorWrite(
                        writeAccessGranted = writeAllowed,
                        isLocalPath = path.isLinuxPath,
                        canRequestAllFilesAccess = path.isLinuxPath &&
                            runCatching {
                                path.normalize().startsWith(
                                    Environment.getExternalStorageDirectory().toPath().normalize()
                                )
                            }.getOrDefault(false) &&
                            !Environment.isExternalStorageManager()
                    )
                ) {
                    TextEditorWriteDecision.REQUEST_ALL_FILES_ACCESS -> {
                        writeRequestPending.set(false)
                        _writeFileResults.tryEmit(TextEditorWriteResult.AccessRequired)
                        return@launch
                    }
                    TextEditorWriteDecision.READ_ONLY -> {
                        writeRequestPending.set(false)
                        _writeFileResults.tryEmit(TextEditorWriteResult.Failed)
                        return@launch
                    }
                    TextEditorWriteDecision.WRITE -> Unit
                }
                check(_writeFileState.value.isReady)
                updateEditorState(editorState.beginSave(text))
                saveStarted = true
                val argument = path to text
                _writeFileState.value = ActionState.Running(argument)
                val bytes = withContext(Dispatchers.Default) {
                    text.toByteArray(encoding.value)
                }
                FileOperationService.write(path, bytes, context) { successful ->
                    if (successful) {
                        loadJob?.cancel()?.also { loadJob = null }
                        reloadJob?.cancel()?.also { reloadJob = null }
                        _bytesState.value = DataState.Success(bytes)
                    }
                    updateEditorState(editorState.completeSave(successful))
                    _writeFileState.value = ActionState.Ready()
                    writeRequestPending.set(false)
                    _writeFileResults.tryEmit(
                        if (successful) TextEditorWriteResult.Saved else TextEditorWriteResult.Failed
                    )
                }
            } catch (exception: Exception) {
                if (saveStarted && editorState.inFlightText != null) {
                    updateEditorState(editorState.completeSave(false))
                    _writeFileState.value = ActionState.Ready()
                }
                writeRequestPending.set(false)
                throw exception
            }
        }
    }

    private var editTextSavedState: Parcelable? = null

    fun setEditTextSavedState(editTextSavedState: Parcelable?) {
        this.editTextSavedState = editTextSavedState
    }

    fun removeEditTextSavedState(): Parcelable? {
        val savedState = editTextSavedState
        editTextSavedState = null
        return savedState
    }

    companion object {
        private const val MAX_FILE_SIZE = 1024 * 1024.toLong()
    }
}

internal enum class TextEditorWriteResult {
    Saved,
    AccessRequired,
    Failed
}
