package com.wisso.wizefiles.feature.ebook

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
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class EbookViewerViewModel(application: Application) : AndroidViewModel(application) {
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
                val opened = open(path, mimeType)
                session = opened
                mutableState.value = State.Ready(opened)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: MobiConversionException) {
                mutableState.value = State.Error(exception.failure.toViewerFailure())
            } catch (_: UnsafeEpubException) {
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

    fun currentSession(): Session? = session

    private suspend fun open(path: AppPath, mimeType: MimeType): Session {
        val legacyPath = path.toLegacyPathOrNull() ?: throw IOException("Path is unavailable")
        val format = FileFormat.fromFileName(path.name)
            ?: FileFormat.fromMimeType(mimeType)
            ?: throw IOException("Unsupported ebook")
        if (format.family != FileFormat.Family.EBOOK) throw IOException("Unsupported ebook")
        val expectedSize = try {
            legacyPath.size()
        } catch (_: IOException) {
            null
        }
        val staging = FormatStagingStore(getApplication<Application>().cacheDir)
        try {
            val extension = if (format == FileFormat.EPUB) "epub" else "mobi"
            val stagedSource = staging.stageFile(
                fileName = "book.$extension",
                input = legacyPath.newInputStream(),
                expectedSizeBytes = expectedSize,
                maxBytes = EpubSafetyValidator.MAX_SOURCE_BYTES
            )
            val epub = if (format == FileFormat.EPUB) {
                EpubSafetyValidator.validate(stagedSource)
                stagedSource
            } else {
                val bundle = File(staging.sessionDirectory, "converted-bundle")
                if (!bundle.mkdir()) throw IOException("Unable to create conversion directory")
                MobiConverterNative.convertToBundle(stagedSource, bundle)
                EbookArchiveBuilder.createEpub(bundle, File(staging.sessionDirectory, "converted.epub"))
            }

            val assetRetriever = AssetRetriever(getApplication<Application>().contentResolver, OfflineHttpClient)
            val asset = assetRetriever.retrieve(epub).getOrElse {
                throw IOException("Unable to read EPUB asset")
            }
            val parser = DefaultPublicationParser(
                getApplication<Application>(),
                OfflineHttpClient,
                assetRetriever,
                null
            )
            val publication = PublicationOpener(parser)
                .open(asset, allowUserInteraction = false)
                .getOrElse {
                    asset.close()
                    throw IOException("Unable to parse EPUB publication")
                }
            if (publication.readingOrder.isEmpty()) {
                publication.close()
                throw IOException("EPUB has no readable content")
            }
            return Session(publication, EpubNavigatorFactory(publication), staging)
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

    enum class Failure {
        ENCRYPTED,
        PRINT_REPLICA,
        LIMIT_EXCEEDED,
        UNSAFE_OR_DAMAGED,
        UNAVAILABLE,
        OPEN_FAILED
    }

    class Session(
        val publication: Publication,
        val navigatorFactory: EpubNavigatorFactory,
        private val staging: FormatStagingStore
    ) : AutoCloseable {
        override fun close() {
            publication.close()
            staging.close()
        }
    }

    private fun MobiFailure.toViewerFailure(): Failure = when (this) {
        MobiFailure.ENCRYPTED -> Failure.ENCRYPTED
        MobiFailure.PRINT_REPLICA -> Failure.PRINT_REPLICA
        MobiFailure.LIMIT_EXCEEDED -> Failure.LIMIT_EXCEEDED
        MobiFailure.INVALID_DOCUMENT -> Failure.UNSAFE_OR_DAMAGED
        MobiFailure.CONVERSION_FAILED -> Failure.OPEN_FAILED
    }
}
