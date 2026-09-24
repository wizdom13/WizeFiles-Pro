package com.wisso.wizefiles.feature.webdocument

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.advancedformats.FileFormat
import com.wisso.wizefiles.feature.advancedformats.staging.FormatStagingStore
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.common.size
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WebDocumentViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState
    private var loadJob: Job? = null
    private var session: Session? = null

    fun load(path: AppPath, mimeType: MimeType) {
        if (mutableState.value is State.Ready || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                session?.close()
                session = open(path, mimeType).also { mutableState.value = State.Ready(it) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: UnsafeSavedWebDocumentException) {
                mutableState.value = State.Error(Failure.UNSAFE_OR_DAMAGED)
            } catch (_: LinkageError) {
                mutableState.value = State.Error(Failure.UNAVAILABLE)
            } catch (_: IOException) {
                mutableState.value = State.Error(Failure.OPEN_FAILED)
            } catch (_: RuntimeException) {
                mutableState.value = State.Error(Failure.OPEN_FAILED)
            } finally {
                loadJob = null
            }
        }
    }

    fun retry(path: AppPath, mimeType: MimeType) {
        if (mutableState.value is State.Error) {
            mutableState.value = State.Idle
            load(path, mimeType)
        }
    }

    private suspend fun open(path: AppPath, mimeType: MimeType): Session {
        val source = path.toLegacyPathOrNull() ?: throw IOException("Path is unavailable")
        val format = FileFormat.fromFileName(path.name) ?: FileFormat.fromMimeType(mimeType)
        if (format?.family != FileFormat.Family.WEB_DOCUMENT) throw IOException("Unsupported saved document")
        val staging = FormatStagingStore(getApplication<Application>().cacheDir)
        try {
            val bundle = when (format) {
                FileFormat.HTML -> {
                    val expected = runCatching { source.size() }.getOrNull()
                    val staged = staging.stageFile("document.html", source.newInputStream(), expected, MAX_HTML_SOURCE_BYTES)
                    SavedWebDocumentBundle.singleHtml(staged)
                }
                FileFormat.MHTML -> {
                    val expected = runCatching { source.size() }.getOrNull()
                    val staged = staging.stageFile("document.mhtml", source.newInputStream(), expected, MhtmlBundleParser.MAX_SOURCE_BYTES)
                    MhtmlBundleParser.parse(staged, File(staging.sessionDirectory, "mhtml-parts"))
                }
                FileFormat.CHM, FileFormat.MAFF -> SafeWebArchiveExtractor.extract(source, File(staging.sessionDirectory, "bundle"))
                else -> throw IOException("Unsupported saved document")
            }
            return Session(bundle, staging)
        } catch (exception: Exception) {
            staging.close()
            throw exception
        }
    }

    override fun onCleared() {
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

    enum class Failure { UNSAFE_OR_DAMAGED, UNAVAILABLE, OPEN_FAILED }

    class Session(val bundle: SavedWebDocumentBundle, private val staging: FormatStagingStore) : AutoCloseable {
        override fun close() = staging.close()
    }

    private companion object {
        const val MAX_HTML_SOURCE_BYTES = 32L * 1024 * 1024
    }
}

