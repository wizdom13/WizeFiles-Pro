package com.wisso.wizefiles.feature.details.audio

import android.media.MediaMetadataRetriever
import android.os.Build
import java.time.Duration
import com.wisso.wizefiles.core.android.compat.METADATA_KEY_SAMPLERATE
import com.wisso.wizefiles.core.android.compat.use
import com.wisso.wizefiles.feature.details.PathObserverLiveData
import com.wisso.wizefiles.feature.details.extractMetadataNotBlank
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.setDataSource
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.util.backgroundExecutor

class AudioInfoLiveData(path: AppPath) : PathObserverLiveData<Stateful<AudioInfo>>(path) {
    init {
        loadValue()
        observe()
    }

    override fun loadValue() {
        value = Loading(value?.value)
        backgroundExecutor.execute {
            val value = try {
                val audioInfo = MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
                    val title = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_TITLE
                    )
                    val artist = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_ARTIST
                    )
                    val album = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_ALBUM
                    )
                    val albumArtist = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST
                    )
                    val composer = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_COMPOSER
                    )
                    val discNumber = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER
                    )
                    val trackNumber = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER
                    )
                    val year = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_YEAR
                    )
                    val genre = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_GENRE
                    )
                    val duration = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull()?.let { Duration.ofMillis(it) }
                    val bitRate = retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_BITRATE
                    )?.toIntOrNull()
                    val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        retriever.extractMetadataNotBlank(
                            MediaMetadataRetriever::class.METADATA_KEY_SAMPLERATE
                        )?.toIntOrNull()
                    } else {
                        null
                    }
                    AudioInfo(
                        title, artist, album, albumArtist, composer, discNumber, trackNumber, year,
                        genre, duration, bitRate, sampleRate
                    )
                }
                Success(audioInfo)
            } catch (e: Exception) {
                Failure(valueCompat.value, e)
            }
            postValue(value)
        }
    }
}
