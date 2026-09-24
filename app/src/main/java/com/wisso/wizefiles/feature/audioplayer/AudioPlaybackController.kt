package com.wisso.wizefiles.feature.audioplayer

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class AudioPlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    fun connect(onReady: () -> Unit, onError: (Throwable) -> Unit) {
        if (controller != null) {
            onReady()
            return
        }
        if (controllerFuture != null) return
        val token = SessionToken(
            appContext,
            ComponentName(appContext, AudioPlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess {
                        controller = it
                        controllerFuture = null
                        onReady()
                    }
                    .onFailure {
                        controllerFuture = null
                        onError(it)
                    }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    fun addListener(listener: Player.Listener) {
        controller?.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        controller?.removeListener(listener)
    }

    fun togglePlayPause() {
        controller?.let {
            when {
                it.isPlaying -> it.pause()
                it.playbackState == Player.STATE_ENDED -> {
                    it.seekToDefaultPosition()
                    it.play()
                }
                else -> it.play()
            }
        }
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(MIN_SPEED, MAX_SPEED))
    }

    fun loadSession(sessionId: String, onError: (Throwable) -> Unit) {
        sendCommand(
            AudioPlaybackService.COMMAND_LOAD_SESSION,
            Bundle().apply {
                putString(AudioPlaybackService.ARG_SESSION_ID, sessionId)
            },
            onError
        )
    }

    fun stopPlayback(onError: (Throwable) -> Unit = {}) {
        sendCommand(AudioPlaybackService.COMMAND_STOP_PLAYBACK, Bundle.EMPTY, onError)
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun isPlaying(): Boolean = controller?.isPlaying == true

    fun position(): Long = controller?.currentPosition ?: 0L

    fun duration(): Long = (controller?.duration ?: 0L).coerceAtLeast(0L)

    fun playbackSpeed(): Float = controller?.playbackParameters?.speed ?: 1f

    fun currentSourceIndex(): Int =
        controller?.currentMediaItem?.mediaId?.toIntOrNull() ?: -1

    fun playSourceIndex(sourceIndex: Int) {
        val activeController = controller ?: return
        val playerIndex = (0 until activeController.mediaItemCount).firstOrNull {
            activeController.getMediaItemAt(it).mediaId.toIntOrNull() == sourceIndex
        } ?: return
        activeController.seekToDefaultPosition(playerIndex)
        activeController.play()
    }

    fun currentMediaMetadata() = controller?.mediaMetadata

    fun hasPrevious(): Boolean = controller?.hasPreviousMediaItem() == true

    fun hasNext(): Boolean = controller?.hasNextMediaItem() == true

    fun playbackState(): Int = controller?.playbackState ?: Player.STATE_IDLE

    fun release() {
        controller?.release()
        controller = null
        controllerFuture?.cancel(true)
        controllerFuture = null
    }

    private fun sendCommand(
        action: String,
        arguments: Bundle,
        onError: (Throwable) -> Unit
    ) {
        val activeController = controller ?: return
        val future = activeController.sendCustomCommand(
            SessionCommand(action, Bundle.EMPTY),
            arguments
        )
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { result ->
                        if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                            onError(IllegalStateException("Audio command failed: $action"))
                        }
                    }
                    .onFailure(onError)
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private companion object {
        const val MIN_SPEED = 0.1f
        const val MAX_SPEED = 5f
    }
}
