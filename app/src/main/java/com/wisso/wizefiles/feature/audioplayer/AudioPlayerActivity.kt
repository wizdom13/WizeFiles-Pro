package com.wisso.wizefiles.feature.audioplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.model.loadFileItem
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.databinding.ActivityAudioPlayerBinding
import com.wisso.wizefiles.feature.details.FilePropertiesDialogFragment
import com.wisso.wizefiles.feature.filebrowser.EXTRA_EXTERNAL_DISPLAY_NAME
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.createSendStreamIntent
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.withChooser
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class AudioPlayerActivity : BaseThemedActivity() {
    private lateinit var binding: ActivityAudioPlayerBinding
    private lateinit var items: MutableList<AudioPlaybackItem>
    private lateinit var artworkAdapter: AudioArtworkPagerAdapter
    private lateinit var sessionId: String
    private lateinit var playbackController: AudioPlaybackController
    private var currentIndex = 0
    private var controllerReady = false
    private var userSeeking = false
    private val progressHandler = Handler(Looper.getMainLooper())

    private val progressTicker = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updatePlaybackUi()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val sourceIndex = mediaItem?.mediaId?.toIntOrNull() ?: return
            selectSource(sourceIndex)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateMetadata(mediaMetadata)
        }

        override fun onPlayerError(error: PlaybackException) {
            binding.bufferingProgress.isVisible = false
            binding.statusText.setText(R.string.audio_player_failed)
            binding.statusText.isVisible = true
            showToast(R.string.audio_player_failed)
        }
    }

    private val currentItem: AudioPlaybackItem
        get() = items[currentIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = resolveLaunch() ?: run {
            finish()
            return
        }
        sessionId = launch.first
        items = launch.second.items.toMutableList()
        currentIndex = savedInstanceState?.getInt(STATE_INDEX)
            ?.coerceIn(items.indices)
            ?: launch.second.initialIndex.coerceIn(items.indices)

        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.audio_player_title)

        artworkAdapter = AudioArtworkPagerAdapter(items)
        binding.artworkPager.adapter = artworkAdapter
        binding.artworkPager.setCurrentItem(currentIndex, false)
        binding.artworkPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (position !in items.indices) return
                    currentIndex = position
                    updateItemFallbackUi()
                    if (
                        controllerReady &&
                        playbackController.currentSourceIndex() != position
                    ) {
                        playbackController.playSourceIndex(position)
                    }
                }
            }
        )

        binding.previousButton.setOnClickListener { playbackController.previous() }
        binding.playPauseButton.setOnClickListener { playbackController.togglePlayPause() }
        binding.nextButton.setOnClickListener { playbackController.next() }
        binding.seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) binding.elapsedText.text = formatDuration(progress.toLong() * 1000)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    userSeeking = false
                    playbackController.seekTo(seekBar.progress.toLong() * 1000)
                    updateProgress()
                }
            }
        )

        updateItemFallbackUi()
        playbackController = AudioPlaybackController(this)
        playbackController.connect(
            onReady = {
                controllerReady = true
                playbackController.addListener(playerListener)
                playbackController.loadSession(sessionId, ::handleControllerError)
                updatePlaybackUi()
            },
            onError = ::handleControllerError
        )
    }

    override fun onStart() {
        super.onStart()
        progressHandler.removeCallbacks(progressTicker)
        progressHandler.post(progressTicker)
    }

    override fun onStop() {
        progressHandler.removeCallbacks(progressTicker)
        super.onStop()
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressTicker)
        if (::playbackController.isInitialized) {
            playbackController.removeListener(playerListener)
            playbackController.release()
        }
        if (::binding.isInitialized) binding.artworkPager.adapter = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_INDEX, currentIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_audio_player, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_audio_share -> {
                shareCurrent()
                true
            }
            R.id.action_audio_speed -> {
                showPlaybackSpeedDialog()
                true
            }
            R.id.action_audio_set_ringtone -> {
                startActivity(
                    SetRingtoneActivity.createIntent(
                        this,
                        currentItem.path,
                        currentItem.mimeType,
                        currentItem.userFacingName
                    )
                )
                true
            }
            R.id.action_audio_properties -> {
                showProperties()
                true
            }
            R.id.action_audio_open_with -> {
                openExternally()
                true
            }
            R.id.action_audio_stop -> {
                playbackController.stopPlayback(::handleControllerError)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun resolveLaunch(): Pair<String, AudioPlaybackSession>? {
        val requestedSessionId = intent.getStringExtra(AudioPlayerIntents.EXTRA_SESSION_ID)
            ?: AudioPlaybackRuntimeStore.activeSessionId()
        val existingSession = AudioPlaybackSessionStore.get(requestedSessionId)
        if (requestedSessionId != null && existingSession?.items?.isNotEmpty() == true) {
            return requestedSessionId to existingSession
        }
        val fallback = AudioPlayerIntents.fallbackItem(
            intent.extraPath,
            intent.type,
            intent.getStringExtra(EXTRA_EXTERNAL_DISPLAY_NAME)
        ) ?: return null
        val fallbackSessionId = AudioPlaybackSessionStore.create(listOf(fallback), fallback)
        val fallbackSession = AudioPlaybackSessionStore.get(fallbackSessionId) ?: return null
        return fallbackSessionId to fallbackSession
    }

    private fun selectSource(sourceIndex: Int) {
        if (sourceIndex !in items.indices) return
        currentIndex = sourceIndex
        if (binding.artworkPager.currentItem != sourceIndex) {
            binding.artworkPager.setCurrentItem(sourceIndex, true)
        }
        updateItemFallbackUi()
    }

    private fun updatePlaybackUi() {
        if (!controllerReady) return
        val sourceIndex = playbackController.currentSourceIndex()
        if (sourceIndex in items.indices) selectSource(sourceIndex)
        val isPlaying = playbackController.isPlaying()
        binding.playPauseButton.setImageResource(
            if (isPlaying) R.drawable.ic_audio_pause_24dp else R.drawable.ic_audio_play_24dp
        )
        binding.playPauseButton.contentDescription = getString(
            if (isPlaying) R.string.audio_player_pause else R.string.audio_player_play
        )
        binding.previousButton.isEnabled = playbackController.hasPrevious()
        binding.nextButton.isEnabled = playbackController.hasNext()
        binding.bufferingProgress.isVisible =
            playbackController.playbackState() == Player.STATE_BUFFERING
        if (playbackController.playbackState() == Player.STATE_READY) {
            binding.statusText.isVisible = false
        }
        updateMetadata(playbackController.currentMediaMetadata())
        updateProgress()
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        val title = (
            metadata?.title?.toString()?.takeIf(String::isNotBlank)
                ?: metadata?.displayTitle?.toString()?.takeIf(String::isNotBlank)
            )
            ?: currentItem.userFacingName
        val artist = metadata?.artist?.toString()?.takeIf(String::isNotBlank)
        val album = metadata?.albumTitle?.toString()?.takeIf(String::isNotBlank)
        binding.titleText.text = title
        binding.artistText.text = artist ?: getString(R.string.audio_player_unknown_artist)
        binding.albumText.text = album
        binding.albumText.isVisible = album != null
        artworkAdapter.setArtwork(currentIndex, metadata?.artworkData)
    }

    private fun updateItemFallbackUi() {
        binding.titleText.text = currentItem.userFacingName
        binding.artistText.setText(R.string.audio_player_unknown_artist)
        binding.albumText.isVisible = false
        binding.positionText.text = getString(
            R.string.audio_player_position,
            currentIndex + 1,
            items.size
        )
    }

    private fun updateProgress() {
        if (!controllerReady) return
        val duration = playbackController.duration()
        val position = playbackController.position().coerceIn(0L, duration.coerceAtLeast(0L))
        val durationSeconds = (duration / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        binding.seekBar.max = durationSeconds
        binding.seekBar.isEnabled = durationSeconds > 0
        if (!userSeeking) {
            binding.seekBar.progress =
                (position / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            binding.elapsedText.text = formatDuration(position)
        }
        binding.durationText.text = formatDuration(duration)
    }

    private fun showPlaybackSpeedDialog() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = speeds.map {
            getString(R.string.media_preview_speed_value, it.toString().removeSuffix(".0"))
        }.toTypedArray()
        val currentSpeed = playbackController.playbackSpeed()
        val checked = speeds.indices.minByOrNull { abs(speeds[it] - currentSpeed) } ?: 2
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.audio_player_speed)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                playbackController.setPlaybackSpeed(speeds[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun shareCurrent() {
        val legacyPath = legacyPathOrNull() ?: run {
            showToast(R.string.audio_player_unavailable)
            return
        }
        startActivitySafe(
            legacyPath.fileProviderUri
                .createSendStreamIntent(currentItem.mimeType)
                .withChooser(getString(R.string.audio_player_share))
        )
    }

    private fun openExternally() {
        val legacyPath = legacyPathOrNull() ?: run {
            showToast(R.string.audio_player_unavailable)
            return
        }
        playbackController.pause()
        startActivitySafe(
            legacyPath.fileProviderUri
                .createViewIntent(currentItem.mimeType)
                .withChooser(getString(R.string.audio_player_open_with))
        )
    }

    private fun showProperties() {
        val cached = currentItem.fileItem
        if (cached != null) {
            FilePropertiesDialogFragment.show(cached, this)
            return
        }
        val requestedIndex = currentIndex
        val requestedItem = currentItem
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    requestedItem.path.loadFileItem()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: IOException) {
                    null
                } catch (_: RuntimeException) {
                    null
                }
            }
            if (loaded == null) {
                if (currentIndex == requestedIndex) {
                    showToast(R.string.audio_player_properties_failed)
                }
            } else {
                items[requestedIndex] = requestedItem.copy(fileItem = loaded)
                if (
                    currentIndex == requestedIndex &&
                    !isFinishing &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    FilePropertiesDialogFragment.show(loaded, this@AudioPlayerActivity)
                }
            }
        }
    }

    private fun legacyPathOrNull() =
        try {
            currentItem.path.toLegacyPathOrNull()
        } catch (_: RuntimeException) {
            null
        }

    private fun handleControllerError(error: Throwable) {
        if (!isFinishing && ::binding.isInitialized) {
            binding.statusText.setText(R.string.audio_player_failed)
            binding.statusText.isVisible = true
            showToast(R.string.audio_player_failed)
        }
    }

    private fun applySystemBarInsets() {
        val root = binding.root
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft + bars.left,
                top = initialTop + bars.top,
                right = initialRight + bars.right,
                bottom = initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun formatDuration(durationMs: Long): String =
        DateUtils.formatElapsedTime((durationMs.coerceAtLeast(0L) / 1000))

    private companion object {
        const val STATE_INDEX = "audio_player.index"
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }
}
