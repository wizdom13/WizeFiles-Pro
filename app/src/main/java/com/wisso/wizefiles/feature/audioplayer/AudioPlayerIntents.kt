package com.wisso.wizefiles.feature.audioplayer

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewItem
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.extraPath

object AudioPlayerIntents {
    internal const val EXTRA_SESSION_ID =
        "${BuildConfig.APPLICATION_ID}.extra.AUDIO_PLAYBACK_SESSION_ID"

    fun create(
        context: Context,
        current: FileItem,
        siblings: List<FileItem>
    ): Intent? = create(
        context,
        MediaPreviewItem(current.path, current.mimeType, current),
        siblings.map { MediaPreviewItem(it.path, it.mimeType, it) }
    )

    fun create(
        context: Context,
        current: MediaPreviewItem,
        siblings: List<MediaPreviewItem> = listOf(current)
    ): Intent? {
        val legacyPath = try {
            current.path.toLegacyPathOrNull()
        } catch (_: RuntimeException) {
            null
        } ?: return null
        if (
            InternalOpenPolicy.targetFor(current.mimeType, legacyPath.isArchivePath) !=
            InternalOpenPolicy.Target.AUDIO_PLAYER
        ) {
            return null
        }
        val audioCurrent = current.toAudioPlaybackItem()
        val sessionId = AudioPlaybackSessionStore.create(
            siblings.map { it.toAudioPlaybackItem() },
            audioCurrent
        )
        return Intent(context, AudioPlayerActivity::class.java)
            .setType(current.mimeType.value)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .apply { extraPath = current.path }
    }

    internal fun fallbackItem(
        path: AppPath?,
        mimeType: String?,
        displayName: String? = null
    ): AudioPlaybackItem? {
        if (path == null || mimeType == null) return null
        val parsedMimeType = mimeType.asMimeTypeOrNull() ?: return null
        val userFacingName = displayName?.takeIf(String::isNotBlank) ?: path.name
        if (
            InternalOpenPolicy.targetAfterExtraction(parsedMimeType, userFacingName) !=
            InternalOpenPolicy.Target.AUDIO_PLAYER
        ) {
            return null
        }
        return AudioPlaybackItem(path, parsedMimeType, displayName = displayName)
    }

    private fun MediaPreviewItem.toAudioPlaybackItem(): AudioPlaybackItem =
        AudioPlaybackItem(path, mimeType, fileItem, displayName)
}
