package com.wisso.wizefiles.feature.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.atomic.AtomicBoolean
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media as VlcMedia
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.interfaces.IMedia

/**
 * Keeps Media3 as the primary playback backend and activates LibVLC only for parser/decoder
 * capability failures. The wrapper remains the single [Player] exposed to PlayerView and
 * MediaSession, so controllers, queues, notifications, and transport controls do not change
 * owners when fallback is needed.
 */
@UnstableApi
class FallbackMediaPlayer private constructor(
    private val appContext: Context,
    private val media3Player: ExoPlayer,
    private val errorBridge: Media3ErrorBridge,
    private var fallbackAudioAttributes: AudioAttributes
) : ForwardingSimpleBasePlayer(media3Player) {
    private var libVlcEngine: LibVlcEngine? = null
    private var fallbackActive = false
    private var fallbackIndex = C.INDEX_UNSET
    private var fallbackPlayWhenReady = false
    private var fallbackSpeed = 1f
    private var fallbackState = Player.STATE_IDLE
    private var fallbackLoading = false
    private var fallbackSuppressed = false
    private var fallbackError: PlaybackException? = null
    private var videoOutput: Any? = null
    private var released = false

    private val audioFocus = LibVlcAudioFocus(
        context = appContext,
        attributes = { fallbackAudioAttributes },
        onTransientLoss = {
            if (fallbackActive) {
                fallbackSuppressed = true
                libVlcEngine?.pause()
                invalidateState()
            }
        },
        onPermanentLoss = {
            if (fallbackActive) {
                fallbackPlayWhenReady = false
                fallbackSuppressed = false
                libVlcEngine?.pause()
                invalidateState()
            }
        },
        onGain = {
            if (fallbackActive && fallbackPlayWhenReady) {
                fallbackSuppressed = false
                libVlcEngine?.play()
                invalidateState()
            }
        }
    )

    init {
        errorBridge.onError = ::onMedia3Error
    }

    override fun getState(): State {
        val base = super.getState()
        if (!fallbackActive) return base
        val engine = libVlcEngine
        val state = base.buildUpon()
            .setPlayerError(fallbackError)
            .setPlaybackState(if (fallbackError == null) fallbackState else Player.STATE_IDLE)
            .setIsLoading(fallbackError == null && fallbackLoading)
            .setPlayWhenReady(
                fallbackPlayWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            .setPlaybackSuppressionReason(
                if (fallbackSuppressed) {
                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                } else {
                    Player.PLAYBACK_SUPPRESSION_REASON_NONE
                }
            )
            .setPlaybackParameters(PlaybackParameters(fallbackSpeed))
            .setCurrentMediaItemIndex(fallbackIndex)
            .setContentPositionMs { engine?.positionMs ?: 0L }
            .setContentBufferedPositionMs { engine?.positionMs ?: 0L }
            .setTotalBufferedDurationMs { 0L }

        if (media3Player.mediaItemCount > 0) {
            state.setPlaylist(
                (0 until media3Player.mediaItemCount).map { index ->
                    val item = media3Player.getMediaItemAt(index)
                    MediaItemData.Builder("libvlc:$index:${item.mediaId}")
                        .setMediaItem(item)
                        .setMediaMetadata(metadataFor(index, item))
                        .setDurationUs(
                            if (index == fallbackIndex) durationUs(engine?.durationMs ?: -1L)
                            else C.TIME_UNSET
                        )
                        .setIsSeekable(index == fallbackIndex && engine?.isSeekable == true)
                        .build()
                }
            )
        }
        return state.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (!fallbackActive) return super.handleSetPlayWhenReady(playWhenReady)
        fallbackPlayWhenReady = playWhenReady
        fallbackSuppressed = false
        if (playWhenReady) {
            if (audioFocus.request()) libVlcEngine?.play() else fallbackPlayWhenReady = false
        } else {
            libVlcEngine?.pause()
            audioFocus.abandon()
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (!fallbackActive) return super.handlePrepare()
        if (fallbackState == Player.STATE_IDLE || fallbackState == Player.STATE_ENDED) {
            retryCurrentFallback()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (!fallbackActive) return super.handleStop()
        fallbackPlayWhenReady = false
        fallbackSuppressed = false
        fallbackState = Player.STATE_IDLE
        fallbackLoading = false
        libVlcEngine?.stop()
        audioFocus.abandon()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        if (released) return Futures.immediateVoidFuture()
        released = true
        errorBridge.onError = null
        media3Player.removeListener(errorBridge)
        audioFocus.release()
        libVlcEngine?.release()
        libVlcEngine = null
        return super.handleRelease()
    }

    override fun handleSetPlaybackParameters(
        playbackParameters: PlaybackParameters
    ): ListenableFuture<*> {
        if (!fallbackActive) return super.handleSetPlaybackParameters(playbackParameters)
        fallbackSpeed = playbackParameters.speed
        libVlcEngine?.rate = fallbackSpeed
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        if (!fallbackActive) return super.handleSetVolume(volume)
        libVlcEngine?.volume = (volume * 100).toInt().coerceIn(0, 100)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetAudioAttributes(
        audioAttributes: AudioAttributes,
        handleAudioFocus: Boolean
    ): ListenableFuture<*> {
        fallbackAudioAttributes = audioAttributes
        if (!fallbackActive) {
            return super.handleSetAudioAttributes(audioAttributes, handleAudioFocus)
        }
        audioFocus.rebuildRequest()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
        this.videoOutput = videoOutput
        if (!fallbackActive) return super.handleSetVideoOutput(videoOutput)
        libVlcEngine?.setVideoOutput(videoOutput)
        return Futures.immediateVoidFuture()
    }

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
        if (videoOutput == null || this.videoOutput === videoOutput) this.videoOutput = null
        if (!fallbackActive) return super.handleClearVideoOutput(videoOutput)
        libVlcEngine?.setVideoOutput(null)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        deactivateFallback(reattachMedia3Output = true)
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        deactivateFallback(reattachMedia3Output = true)
        return super.handleAddMediaItems(index, mediaItems)
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int
    ): ListenableFuture<*> {
        deactivateFallback(reattachMedia3Output = true)
        return super.handleMoveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        deactivateFallback(reattachMedia3Output = true)
        return super.handleReplaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        deactivateFallback(reattachMedia3Output = true)
        return super.handleRemoveMediaItems(fromIndex, toIndex)
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (!fallbackActive) return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        val targetIndex = mediaItemIndex.takeUnless { it == C.INDEX_UNSET } ?: fallbackIndex
        val targetPosition = positionMs.takeUnless { it == C.TIME_UNSET } ?: 0L
        if (targetIndex == fallbackIndex) {
            libVlcEngine?.positionMs = targetPosition.coerceAtLeast(0L)
            invalidateState()
        } else {
            switchToMedia3(targetIndex, targetPosition, fallbackPlayWhenReady)
        }
        return Futures.immediateVoidFuture()
    }

    private fun onMedia3Error(error: PlaybackException) {
        if (released || fallbackActive || !LibVlcFallbackPolicy.shouldFallback(error)) return
        val index = media3Player.currentMediaItemIndex
        if (index !in 0 until media3Player.mediaItemCount) return
        val item = media3Player.getMediaItemAt(index)
        val uri = item.localConfiguration?.uri ?: return
        val resumePosition = media3Player.currentPosition.coerceAtLeast(0L)
        val resumeWhenReady = media3Player.playWhenReady
        val resumeSpeed = media3Player.playbackParameters.speed

        fallbackActive = true
        fallbackIndex = index
        fallbackPlayWhenReady = resumeWhenReady
        fallbackSpeed = resumeSpeed
        fallbackState = Player.STATE_BUFFERING
        fallbackLoading = true
        fallbackSuppressed = false
        fallbackError = null
        media3Player.playWhenReady = false
        detachMedia3VideoOutput()
        media3Player.stop()

        try {
            val engine = libVlcEngine ?: LibVlcEngine(appContext, ::onLibVlcEvent).also {
                libVlcEngine = it
            }
            engine.setVideoOutput(videoOutput)
            if (resumeWhenReady) {
                if (!audioFocus.request()) fallbackPlayWhenReady = false
            }
            engine.open(uri, resumePosition, resumeSpeed)
        } catch (failure: Throwable) {
            fallbackLoading = false
            fallbackState = Player.STATE_IDLE
            fallbackError = PlaybackException(
                "LibVLC fallback initialization failed",
                failure,
                PlaybackException.ERROR_CODE_DECODING_FAILED
            )
        }
        invalidateState()
    }

    private fun onLibVlcEvent(event: LibVlcEngine.Event) {
        if (!fallbackActive || released) return
        when (event) {
            LibVlcEngine.Event.OPENING -> {
                fallbackState = Player.STATE_BUFFERING
                fallbackLoading = true
            }
            LibVlcEngine.Event.BUFFERING -> fallbackLoading = true
            LibVlcEngine.Event.READY -> {
                fallbackState = Player.STATE_READY
                fallbackLoading = false
                if (!fallbackPlayWhenReady || fallbackSuppressed) libVlcEngine?.pause()
            }
            LibVlcEngine.Event.PAUSED -> {
                fallbackState = Player.STATE_READY
                fallbackLoading = false
            }
            LibVlcEngine.Event.ENDED -> {
                fallbackState = Player.STATE_ENDED
                fallbackLoading = false
                audioFocus.abandon()
                val nextIndex = fallbackIndex + 1
                if (nextIndex < media3Player.mediaItemCount) {
                    switchToMedia3(nextIndex, 0L, playWhenReady = true)
                    return
                }
                fallbackPlayWhenReady = false
            }
            LibVlcEngine.Event.STOPPED -> {
                if (fallbackState != Player.STATE_ENDED) fallbackState = Player.STATE_IDLE
                fallbackLoading = false
            }
            LibVlcEngine.Event.ERROR -> {
                fallbackState = Player.STATE_IDLE
                fallbackLoading = false
                fallbackPlayWhenReady = false
                audioFocus.abandon()
                fallbackError = PlaybackException(
                    "LibVLC could not decode this media",
                    null,
                    PlaybackException.ERROR_CODE_DECODING_FAILED
                )
            }
            LibVlcEngine.Event.UPDATED -> Unit
        }
        invalidateState()
    }

    private fun retryCurrentFallback() {
        val index = fallbackIndex
        if (index !in 0 until media3Player.mediaItemCount) return
        val item = media3Player.getMediaItemAt(index)
        val uri = item.localConfiguration?.uri ?: return
        fallbackError = null
        fallbackState = Player.STATE_BUFFERING
        fallbackLoading = true
        if (fallbackPlayWhenReady && !audioFocus.request()) fallbackPlayWhenReady = false
        libVlcEngine?.open(uri, 0L, fallbackSpeed)
        invalidateState()
    }

    private fun switchToMedia3(index: Int, positionMs: Long, playWhenReady: Boolean) {
        if (index !in 0 until media3Player.mediaItemCount) return
        deactivateFallback(reattachMedia3Output = true)
        media3Player.seekTo(index, positionMs.coerceAtLeast(0L))
        media3Player.prepare()
        media3Player.playWhenReady = playWhenReady
        invalidateState()
    }

    private fun deactivateFallback(reattachMedia3Output: Boolean) {
        if (!fallbackActive) return
        fallbackActive = false
        audioFocus.abandon()
        libVlcEngine?.stop()
        libVlcEngine?.setVideoOutput(null)
        fallbackIndex = C.INDEX_UNSET
        fallbackPlayWhenReady = false
        fallbackSuppressed = false
        fallbackLoading = false
        fallbackError = null
        if (reattachMedia3Output) attachMedia3VideoOutput()
        invalidateState()
    }

    private fun metadataFor(index: Int, item: MediaItem): MediaMetadata {
        if (index != fallbackIndex) return item.mediaMetadata
        val engine = libVlcEngine ?: return item.mediaMetadata
        return item.mediaMetadata.buildUpon()
            .setTitle(engine.title ?: item.mediaMetadata.title)
            .setArtist(engine.artist ?: item.mediaMetadata.artist)
            .setAlbumTitle(engine.album ?: item.mediaMetadata.albumTitle)
            .build()
    }

    private fun detachMedia3VideoOutput() {
        when (val output = videoOutput) {
            is SurfaceView -> media3Player.clearVideoSurfaceView(output)
            is TextureView -> media3Player.clearVideoTextureView(output)
            is SurfaceHolder -> media3Player.clearVideoSurfaceHolder(output)
            is Surface -> media3Player.clearVideoSurface(output)
        }
    }

    private fun attachMedia3VideoOutput() {
        when (val output = videoOutput) {
            is SurfaceView -> media3Player.setVideoSurfaceView(output)
            is TextureView -> media3Player.setVideoTextureView(output)
            is SurfaceHolder -> media3Player.setVideoSurfaceHolder(output)
            is Surface -> media3Player.setVideoSurface(output)
        }
    }

    private fun durationUs(durationMs: Long): Long = when {
        durationMs < 0L -> C.TIME_UNSET
        durationMs > Long.MAX_VALUE / 1_000L -> C.TIME_UNSET
        else -> durationMs * 1_000L
    }

    companion object {
        fun create(
            context: Context,
            audioAttributes: AudioAttributes
        ): FallbackMediaPlayer {
            val bridge = Media3ErrorBridge()
            val media3Player = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(audioAttributes, true)
                setHandleAudioBecomingNoisy(true)
                addListener(bridge)
            }
            return FallbackMediaPlayer(
                context.applicationContext,
                media3Player,
                bridge,
                audioAttributes
            )
        }
    }
}

@UnstableApi
internal object LibVlcFallbackPolicy {
    fun shouldFallback(error: PlaybackException): Boolean = shouldFallback(error.errorCode)

    internal fun shouldFallback(errorCode: Int): Boolean = errorCode in setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
    )
}

@UnstableApi
private class Media3ErrorBridge : Player.Listener {
    var onError: ((PlaybackException) -> Unit)? = null

    override fun onPlayerError(error: PlaybackException) {
        onError?.invoke(error)
    }
}

private class LibVlcEngine(
    context: Context,
    private val onEvent: (Event) -> Unit
) {
    enum class Event { OPENING, BUFFERING, READY, PAUSED, ENDED, STOPPED, ERROR, UPDATED }

    private val libVlc = LibVLC(context, arrayListOf("--no-stats", "--audio-time-stretch"))
    private val player = VlcMediaPlayer(libVlc)
    private var currentMedia: VlcMedia? = null
    private var outputAttached = false
    private var pendingPositionMs = 0L
    private val released = AtomicBoolean(false)

    var positionMs: Long
        get() = player.time.coerceAtLeast(0L)
        set(value) {
            player.setTime(value.coerceAtLeast(0L))
        }
    val durationMs: Long
        get() = player.length
    val isSeekable: Boolean
        get() = player.isSeekable
    var rate: Float
        get() = player.rate
        set(value) {
            player.setRate(value.coerceIn(0.1f, 5f))
        }
    var volume: Int
        get() = player.volume
        set(value) {
            player.setVolume(value.coerceIn(0, 100))
        }
    val title: String?
        get() = currentMedia?.getMeta(IMedia.Meta.Title)?.takeIf(String::isNotBlank)
    val artist: String?
        get() = currentMedia?.getMeta(IMedia.Meta.Artist)?.takeIf(String::isNotBlank)
    val album: String?
        get() = currentMedia?.getMeta(IMedia.Meta.Album)?.takeIf(String::isNotBlank)

    init {
        player.setEventListener { event ->
            val mappedEvent = when (event.type) {
                VlcMediaPlayer.Event.Opening -> Event.OPENING
                VlcMediaPlayer.Event.Buffering -> Event.BUFFERING
                VlcMediaPlayer.Event.Playing -> {
                    if (pendingPositionMs > 0L) {
                        player.setTime(pendingPositionMs)
                        pendingPositionMs = 0L
                    }
                    Event.READY
                }
                VlcMediaPlayer.Event.Paused -> Event.PAUSED
                VlcMediaPlayer.Event.EndReached -> Event.ENDED
                VlcMediaPlayer.Event.Stopped -> Event.STOPPED
                VlcMediaPlayer.Event.EncounteredError -> Event.ERROR
                else -> Event.UPDATED
            }
            onEvent(mappedEvent)
        }
    }

    fun open(uri: android.net.Uri, positionMs: Long, rate: Float) {
        check(!released.get())
        player.stop()
        currentMedia?.release()
        currentMedia = VlcMedia(libVlc, uri).also { media ->
            media.setEventListener { event ->
                if (event.type == IMedia.Event.ParsedChanged) onEvent(Event.UPDATED)
            }
            player.setMedia(media)
            media.parseAsync()
        }
        pendingPositionMs = positionMs.coerceAtLeast(0L)
        player.setRate(rate.coerceIn(0.1f, 5f))
        player.play()
    }

    fun play() {
        if (!released.get()) player.play()
    }

    fun pause() {
        if (!released.get() && player.isPlaying) player.pause()
    }

    fun stop() {
        if (!released.get()) player.stop()
    }

    fun setVideoOutput(output: Any?) {
        if (released.get()) return
        if (outputAttached) {
            player.vlcVout.detachViews()
            outputAttached = false
        }
        when (output) {
            is SurfaceView -> player.vlcVout.setVideoView(output)
            is TextureView -> player.vlcVout.setVideoView(output)
            is SurfaceHolder -> player.vlcVout.setVideoSurface(output.surface, output)
            is Surface -> player.vlcVout.setVideoSurface(output, null)
            null -> return
            else -> return
        }
        player.vlcVout.attachViews()
        outputAttached = true
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        if (outputAttached) player.vlcVout.detachViews()
        player.setEventListener(null)
        player.stop()
        player.release()
        currentMedia?.release()
        currentMedia = null
        libVlc.release()
    }
}

private class LibVlcAudioFocus(
    private val context: Context,
    private val attributes: () -> AudioAttributes,
    private val onTransientLoss: () -> Unit,
    private val onPermanentLoss: () -> Unit,
    private val onGain: () -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var focusRequest: AudioFocusRequest? = null
    private var registeredNoisyReceiver = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onGain()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onTransientLoss()
            AudioManager.AUDIOFOCUS_LOSS -> onPermanentLoss()
        }
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onPermanentLoss()
        }
    }

    fun request(): Boolean {
        val request = focusRequest ?: buildRequest().also { focusRequest = it }
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) registerNoisyReceiver()
        return granted
    }

    fun abandon() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        unregisterNoisyReceiver()
    }

    fun rebuildRequest() {
        abandon()
        focusRequest = buildRequest()
    }

    fun release() {
        abandon()
        focusRequest = null
    }

    private fun buildRequest(): AudioFocusRequest {
        val media3Attributes = attributes()
        val platformAttributes = android.media.AudioAttributes.Builder()
            .setUsage(media3Attributes.usage)
            .setContentType(media3Attributes.contentType)
            .build()
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(platformAttributes)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .build()
    }

    private fun registerNoisyReceiver() {
        if (registeredNoisyReceiver) return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registeredNoisyReceiver = true
    }

    private fun unregisterNoisyReceiver() {
        if (!registeredNoisyReceiver) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        registeredNoisyReceiver = false
    }
}
