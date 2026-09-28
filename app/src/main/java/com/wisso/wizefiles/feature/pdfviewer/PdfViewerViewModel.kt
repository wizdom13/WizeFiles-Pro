// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.pdfviewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wisso.wizefiles.feature.advancedformats.staging.FormatSourcePlanner
import com.wisso.wizefiles.feature.advancedformats.staging.FormatStagingLimitException
import com.wisso.wizefiles.feature.advancedformats.staging.FormatStagingStore
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.common.size
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Prepares a PDF source for AndroidX PDF.
 *
 * AndroidX PDF ultimately renders from a seekable ParcelFileDescriptor. WizeFiles providers may be
 * backed by proxy descriptors, network streams, SAF, root, or cloud implementations, so the reader
 * never passes those provider URIs directly to AndroidX PDF. A directly readable local file is used
 * as-is; every other source is staged into the app-private cache as a regular file first.
 */
class PdfViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState

    private var loadJob: Job? = null
    private var loadedPath: AppPath? = null
    private var session: Session? = null

    fun load(path: AppPath) {
        if (
            loadedPath == path &&
            (mutableState.value is State.Ready || loadJob?.isActive == true)
        ) {
            return
        }
        loadedPath = path
        loadJob?.cancel()

        lateinit var job: Job
        job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            mutableState.value = State.Loading
            var openedSession: Session? = null
            try {
                openedSession = open(path)
                currentCoroutineContext().ensureActive()
                val readySession = requireNotNull(openedSession)
                openedSession = null
                val previous = session
                session = readySession
                previous?.close()
                mutableState.value = State.Ready(readySession)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: FormatStagingLimitException) {
                mutableState.value = State.Error(Failure.TOO_LARGE)
            } catch (_: IOException) {
                mutableState.value = State.Error(Failure.OPEN_FAILED)
            } catch (_: RuntimeException) {
                mutableState.value = State.Error(Failure.OPEN_FAILED)
            } finally {
                openedSession?.close()
                if (loadJob === job) loadJob = null
            }
        }
        loadJob = job
        job.start()
    }

    fun retry(path: AppPath) {
        loadedPath = null
        mutableState.value = State.Idle
        load(path)
    }

    private suspend fun open(path: AppPath): Session {
        val source = path.toLegacyPathOrNull() ?: throw IOException("PDF source is unavailable")

        val directFile = try {
            source.toFile()
        } catch (_: UnsupportedOperationException) {
            null
        } catch (_: RuntimeException) {
            null
        }
        if (directFile?.isFile == true && directFile.canRead()) {
            return Session(directFile, null)
        }

        val expectedSize = try {
            source.size()
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
        val coroutineContext = currentCoroutineContext()
        val staging = FormatStagingStore(getApplication<Application>().cacheDir)
        try {
            val staged = staging.stageFile(
                fileName = "document.pdf",
                input = source.newInputStream(),
                expectedSizeBytes = expectedSize,
                maxBytes = FormatSourcePlanner.MAX_STAGED_SOURCE_BYTES,
                isCancelled = { !coroutineContext.isActive }
            )
            coroutineContext.ensureActive()
            return Session(staged, staging)
        } catch (exception: Exception) {
            staging.close()
            throw exception
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        session?.close()
        session = null
        super.onCleared()
    }

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Ready(val session: Session) : State
        data class Error(val failure: Failure) : State
    }

    enum class Failure {
        TOO_LARGE,
        OPEN_FAILED
    }

    class Session(
        val file: File,
        private val staging: FormatStagingStore?
    ) : AutoCloseable {
        override fun close() {
            staging?.close()
        }
    }
}
