// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.audioplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.feature.playback.FallbackMediaPlayer
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull

@UnstableApi
class AudioPlaybackService : MediaSessionService() {
    private lateinit var player: FallbackMediaPlayer
    private var mediaSession: MediaSession? = null
    private var activeSessionId: String? = null

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            if (!AudioPlaybackControllerPolicy.isAllowed(
                    appPackageName = packageName,
                    controllerPackageName = controller.packageName,
                    isTrusted = controller.isTrusted
                )
            ) {
                return MediaSession.ConnectionResult.reject()
            }
            val commandsBuilder = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            if (AudioPlaybackControllerPolicy.canUseCustomCommands(packageName, controller.packageName)) {
                commandsBuilder
                    .add(SessionCommand(COMMAND_LOAD_SESSION, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_STOP_PLAYBACK, Bundle.EMPTY))
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commandsBuilder.build())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (!AudioPlaybackControllerPolicy.canUseCustomCommands(packageName, controller.packageName)) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            val result = when (customCommand.customAction) {
                COMMAND_LOAD_SESSION -> {
                    val sessionId = args.getString(ARG_SESSION_ID)
                    if (sessionId != null && loadSession(sessionId)) {
                        SessionResult.RESULT_SUCCESS
                    } else {
                        SessionResult.RESULT_ERROR_BAD_VALUE
                    }
                }
                COMMAND_STOP_PLAYBACK -> {
                    stopPlaybackAndService()
                    SessionResult.RESULT_SUCCESS
                }
                else -> SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
            return Futures.immediateFuture(SessionResult(result))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) player.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = FallbackMediaPlayer.create(
            context = this,
            audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        ).apply {
            repeatMode = Player.REPEAT_MODE_OFF
            shuffleModeEnabled = false
            addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(nowPlayingPendingIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (isAllowed(controllerInfo)) {
            mediaSession
        } else {
            null
        }

    private fun isAllowed(controller: MediaSession.ControllerInfo): Boolean =
        AudioPlaybackControllerPolicy.isAllowed(
            appPackageName = packageName,
            controllerPackageName = controller.packageName,
            isTrusted = controller.isTrusted
        )

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopPlaybackAndService()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        val sessionId = activeSessionId
        activeSessionId = null
        AudioPlaybackRuntimeStore.clear(sessionId)
        AudioPlaybackSessionStore.remove(sessionId)
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    private fun loadSession(sessionId: String): Boolean {
        if (sessionId == activeSessionId && player.mediaItemCount > 0) return true
        val playbackSession = AudioPlaybackSessionStore.get(sessionId) ?: return false
        val mediaItems = playbackSession.items.mapIndexedNotNull { sourceIndex, item ->
            item.toMediaItem(sourceIndex)
        }
        if (mediaItems.isEmpty()) return false
        val requestedSourceIndex = playbackSession.initialIndex
        val playerIndex = mediaItems.indexOfFirst {
            it.mediaId.toIntOrNull() == requestedSourceIndex
        }.coerceAtLeast(0)
        val previousSessionId = activeSessionId
        activeSessionId = sessionId
        AudioPlaybackRuntimeStore.setActiveSession(sessionId)
        if (previousSessionId != null && previousSessionId != sessionId) {
            AudioPlaybackSessionStore.remove(previousSessionId)
        }
        player.setMediaItems(mediaItems, playerIndex, 0L)
        player.prepare()
        player.play()
        return true
    }

    private fun AudioPlaybackItem.toMediaItem(sourceIndex: Int): MediaItem? {
        val legacyPath = try {
            path.toLegacyPathOrNull()
        } catch (_: RuntimeException) {
            null
        } ?: return null
        return MediaItem.Builder()
            .setMediaId(sourceIndex.toString())
            .setUri(legacyPath.fileProviderUri)
            .setMimeType(mimeType.value)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setDisplayTitle(userFacingName)
                    .build()
            )
            .build()
    }

    private fun stopPlaybackAndService() {
        player.stop()
        player.clearMediaItems()
        stopSelf()
    }

    private fun nowPlayingPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, AudioPlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        internal const val COMMAND_LOAD_SESSION =
            "com.wisso.wizefiles.command.LOAD_AUDIO_PLAYBACK_SESSION"
        internal const val COMMAND_STOP_PLAYBACK =
            "com.wisso.wizefiles.command.STOP_AUDIO_PLAYBACK"
        internal const val ARG_SESSION_ID =
            "com.wisso.wizefiles.argument.AUDIO_PLAYBACK_SESSION_ID"
    }
}

internal object AudioPlaybackControllerPolicy {
    fun isAllowed(
        appPackageName: String,
        controllerPackageName: String,
        isTrusted: Boolean
    ): Boolean = controllerPackageName == appPackageName || isTrusted

    fun canUseCustomCommands(
        appPackageName: String,
        controllerPackageName: String
    ): Boolean = controllerPackageName == appPackageName
}
