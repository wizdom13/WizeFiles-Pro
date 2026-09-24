package com.wisso.wizefiles.feature.fontviewer

import android.app.Application
import android.graphics.Typeface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wisso.wizefiles.feature.advancedformats.staging.FormatStagingLimitException
import com.wisso.wizefiles.feature.advancedformats.staging.FormatStagingStore
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.common.size
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FontViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState

    private var loadJob: Job? = null
    private var session: Session? = null
    private var loadedPath: AppPath? = null
    private val loadGeneration = FontViewerLoadGeneration()

    fun load(path: AppPath) {
        if (loadedPath == path && (mutableState.value is State.Ready || loadJob?.isActive == true)) {
            return
        }
        loadedPath = path
        loadJob?.cancel()
        val generation = loadGeneration.next()
        lateinit var job: Job
        job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            mutableState.value = State.Loading
            var openedSession: Session? = null
            try {
                openedSession = open(path, generation)
                currentCoroutineContext().ensureActive()
                if (!loadGeneration.isCurrent(generation)) {
                    throw CancellationException("A newer font load replaced this request")
                }
                val readySession = requireNotNull(openedSession)
                openedSession = null
                val previousSession = session
                session = readySession
                previousSession?.close()
                mutableState.value = State.Ready(readySession)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: FormatStagingLimitException) {
                currentCoroutineContext().ensureActive()
                mutableState.value = State.Error(Failure.TOO_LARGE)
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                mutableState.value = State.Error(Failure.OPEN_FAILED)
            } catch (_: RuntimeException) {
                currentCoroutineContext().ensureActive()
                mutableState.value = State.Error(Failure.INVALID_FONT)
            } finally {
                openedSession?.close()
                if (loadJob === job) loadJob = null
            }
        }
        loadJob = job
        job.start()
    }

    fun retry(path: AppPath) {
        mutableState.value = State.Idle
        loadedPath = null
        load(path)
    }

    private suspend fun open(path: AppPath, generation: Long): Session {
        val coroutineContext = currentCoroutineContext()
        val source = path.toLegacyPathOrNull() ?: throw IOException("Font source is unavailable")
        val expectedSize = runCatching { source.size() }.getOrNull()
        val staging = FormatStagingStore(getApplication<Application>().cacheDir)
        try {
            val extension = path.name.substringAfterLast('.', "font").lowercase(Locale.ROOT)
            val staged = staging.stageFile(
                fileName = "preview.$extension",
                input = source.newInputStream(),
                expectedSizeBytes = expectedSize,
                maxBytes = MAX_FONT_BYTES,
                isCancelled = {
                    !coroutineContext.isActive || !loadGeneration.isCurrent(generation)
                }
            )
            coroutineContext.ensureActive()
            val metadata = runCatching { FontMetadataReader.read(staged) }.getOrNull()
            coroutineContext.ensureActive()
            val typeface = Typeface.Builder(staged).apply {
                if (metadata?.format == FontMetadata.Format.TTC) setTtcIndex(0)
            }.build()
            coroutineContext.ensureActive()
            return Session(
                typeface = typeface,
                metadata = metadata,
                sizeBytes = expectedSize ?: staged.length(),
                staging = staging
            )
        } catch (exception: Exception) {
            staging.close()
            throw exception
        }
    }

    override fun onCleared() {
        loadGeneration.invalidate()
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

    enum class Failure { TOO_LARGE, INVALID_FONT, OPEN_FAILED }

    class Session(
        val typeface: Typeface,
        val metadata: FontMetadata?,
        val sizeBytes: Long,
        private val staging: FormatStagingStore
    ) : AutoCloseable {
        override fun close() = staging.close()
    }

    private companion object {
        const val MAX_FONT_BYTES = 128L * 1024 * 1024
    }
}
