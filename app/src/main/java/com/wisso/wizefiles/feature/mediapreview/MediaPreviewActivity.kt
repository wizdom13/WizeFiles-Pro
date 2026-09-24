package com.wisso.wizefiles.feature.mediapreview

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.model.loadFileItem
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.databinding.ActivityMediaPreviewBinding
import com.wisso.wizefiles.feature.details.FilePropertiesDialogFragment
import com.wisso.wizefiles.feature.filebrowser.EXTRA_EXTERNAL_DISPLAY_NAME
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.feature.playback.FallbackMediaPlayer
import com.wisso.wizefiles.provider.archive.isArchivePath
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
class MediaPreviewActivity : BaseThemedActivity() {
    private lateinit var binding: ActivityMediaPreviewBinding
    private lateinit var items: MutableList<MediaPreviewItem>
    private lateinit var pagerAdapter: MediaPreviewPagerAdapter
    private val stateModel: MediaPreviewStateViewModel by viewModels()
    private var sessionId: String? = null
    private var currentIndex = 0
    private var player: FallbackMediaPlayer? = null
    private var activeVideoIndex: Int? = null
    private var isFullscreen = false

    private val currentItem: MediaPreviewItem
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
        if (savedInstanceState != null) {
            stateModel.pageStates.setRotation(
                currentIndex,
                savedInstanceState.getFloat(STATE_ROTATION)
            )
            stateModel.pageStates.setPlayback(
                currentIndex,
                MediaPreviewPlaybackState(
                    positionMs = savedInstanceState.getLong(STATE_PLAYBACK_POSITION),
                    playWhenReady = savedInstanceState.getBoolean(STATE_PLAY_WHEN_READY, true),
                    speed = savedInstanceState.getFloat(STATE_PLAYBACK_SPEED, 1f)
                )
            )
        }
        isFullscreen = savedInstanceState?.getBoolean(STATE_FULLSCREEN) ?: false

        binding = ActivityMediaPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isFullscreen) {
                        setFullscreen(false)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        pagerAdapter = MediaPreviewPagerAdapter(
            items = items,
            rotationFor = stateModel.pageStates::rotationFor,
            onOpenExternally = ::openExternally,
            onPageBound = { position ->
                if (position == currentIndex) {
                    updatePagerInputForCurrentPage()
                    if (
                        lifecycle.currentState.isAtLeast(
                            androidx.lifecycle.Lifecycle.State.STARTED
                        )
                    ) {
                        preparePlayer()
                    }
                }
            },
            onZoomStateChanged = { position, isAtBaseZoom ->
                if (position == currentIndex) {
                    binding.mediaPager.isUserInputEnabled = isAtBaseZoom
                }
            }
        )
        binding.mediaPager.adapter = pagerAdapter
        binding.mediaPager.setCurrentItem(currentIndex, false)
        binding.mediaPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (position == currentIndex) return
                    releasePlayer()
                    currentIndex = position
                    updateCurrentPage()
                }
            }
        )
        updateCurrentPage()
        if (isFullscreen) setFullscreen(true)
    }

    override fun onStart() {
        super.onStart()
        if (
            ::items.isInitialized &&
            currentTarget() == InternalOpenPolicy.Target.VIDEO_PREVIEW
        ) {
            preparePlayer()
        }
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_media_preview, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val target = currentTarget()
        menu.findItem(R.id.action_media_rotate)?.isVisible =
            target == InternalOpenPolicy.Target.IMAGE_PREVIEW
        menu.findItem(R.id.action_media_speed)?.isVisible =
            target == InternalOpenPolicy.Target.VIDEO_PREVIEW
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_media_rotate -> {
                val rotation = stateModel.pageStates.rotateClockwise(currentIndex)
                pagerAdapter.setRotation(currentIndex, rotation)
                true
            }
            R.id.action_media_share -> {
                shareCurrent()
                true
            }
            R.id.action_media_speed -> {
                showPlaybackSpeedDialog()
                true
            }
            R.id.action_media_properties -> {
                showProperties()
                true
            }
            R.id.action_media_open_with -> {
                openExternally(currentItem)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::items.isInitialized) {
            snapshotActivePlayer()
            val playbackState = stateModel.pageStates.playbackFor(currentIndex)
            outState.putInt(STATE_INDEX, currentIndex)
            outState.putFloat(STATE_ROTATION, stateModel.pageStates.rotationFor(currentIndex))
            outState.putLong(STATE_PLAYBACK_POSITION, playbackState.positionMs)
            outState.putBoolean(STATE_PLAY_WHEN_READY, playbackState.playWhenReady)
            outState.putFloat(STATE_PLAYBACK_SPEED, playbackState.speed)
            outState.putBoolean(STATE_FULLSCREEN, isFullscreen)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        releasePlayer()
        if (::binding.isInitialized) {
            binding.mediaPager.adapter = null
        }
        if (::pagerAdapter.isInitialized) {
            pagerAdapter.recycleAll()
        }
        if (isFinishing) MediaPreviewSessionStore.remove(sessionId)
        super.onDestroy()
    }

    private fun resolveLaunch(): Pair<String?, MediaPreviewSession>? {
        val requestedSessionId = intent.getStringExtra(MediaPreviewIntents.EXTRA_SESSION_ID)
        val session = MediaPreviewSessionStore.get(requestedSessionId)
        if (session != null && session.items.isNotEmpty()) return requestedSessionId to session
        val fallback = MediaPreviewIntents.fallbackItem(
            intent.extraPath,
            intent.type,
            intent.getStringExtra(EXTRA_EXTERNAL_DISPLAY_NAME)
        ) ?: return null
        return null to MediaPreviewSession(listOf(fallback), 0)
    }

    private fun updateCurrentPage() {
        supportActionBar?.title = currentItem.userFacingName
        supportActionBar?.subtitle = if (items.size > 1) {
            getString(R.string.media_preview_position, currentIndex + 1, items.size)
        } else {
            null
        }
        pagerAdapter.setRotation(
            currentIndex,
            stateModel.pageStates.rotationFor(currentIndex)
        )
        updatePagerInputForCurrentPage()
        invalidateOptionsMenu()
        if (
            currentTarget() == InternalOpenPolicy.Target.VIDEO_PREVIEW &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        ) {
            preparePlayer()
        }
    }

    private fun currentTarget(): InternalOpenPolicy.Target {
        val path = legacyPathOrNull(currentItem)
            ?: return InternalOpenPolicy.Target.EXTERNAL_APP
        return InternalOpenPolicy.targetFor(
            currentItem.mimeType,
            path.isArchivePath,
            currentItem.userFacingName
        )
    }

    private fun preparePlayer() {
        if (
            currentTarget() != InternalOpenPolicy.Target.VIDEO_PREVIEW ||
            !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        ) {
            return
        }
        val page = pagerAdapter.pageAt(currentIndex) ?: return
        if (player != null && activeVideoIndex == currentIndex) {
            page.playerView.player = player
            return
        }
        releasePlayer()
        val legacyPath = legacyPathOrNull(currentItem) ?: run {
            page.showError(R.string.media_preview_unavailable)
            return
        }
        val preparingIndex = currentIndex
        val playbackState = stateModel.pageStates.playbackFor(preparingIndex)
        player = FallbackMediaPlayer.create(
            context = this,
            audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
        ).also { fallbackPlayer ->
            activeVideoIndex = preparingIndex
            page.playerView.setFullscreenButtonClickListener(::setFullscreen)
            page.playerView.player = fallbackPlayer
            fallbackPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (player === fallbackPlayer && currentIndex == preparingIndex) {
                        if (isFullscreen) setFullscreen(false)
                        releasePlayer()
                        page.showError(R.string.media_preview_video_failed)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (player !== fallbackPlayer) return
                    if (isPlaying) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player === fallbackPlayer && currentIndex == preparingIndex) {
                        page.setLoading(playbackState == Player.STATE_BUFFERING)
                    }
                }
            })
            fallbackPlayer.setMediaItem(
                ExoMediaItem.Builder()
                    .setUri(legacyPath.fileProviderUri)
                    .setMimeType(currentItem.mimeType.value)
                    .build()
            )
            fallbackPlayer.seekTo(playbackState.positionMs)
            fallbackPlayer.setPlaybackSpeed(playbackState.speed)
            fallbackPlayer.playWhenReady = playbackState.playWhenReady
            fallbackPlayer.prepare()
        }
    }

    private fun snapshotActivePlayer() {
        val activePlayer = player ?: return
        val index = activeVideoIndex ?: return
        stateModel.pageStates.setPlayback(
            index,
            MediaPreviewPlaybackState(
                positionMs = activePlayer.currentPosition,
                playWhenReady = activePlayer.playWhenReady,
                speed = activePlayer.playbackParameters.speed
            )
        )
    }

    private fun releasePlayer() {
        val activePlayer = player ?: return
        snapshotActivePlayer()
        activeVideoIndex?.let { pagerAdapter.pageAt(it)?.playerView?.player = null }
        activePlayer.release()
        player = null
        activeVideoIndex = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showPlaybackSpeedDialog() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = speeds.map {
            getString(R.string.media_preview_speed_value, it.toString().removeSuffix(".0"))
        }.toTypedArray()
        val playbackState = stateModel.pageStates.playbackFor(currentIndex)
        val currentSpeed = player?.playbackParameters?.speed ?: playbackState.speed
        val checked = speeds.indices.minByOrNull { abs(speeds[it] - currentSpeed) } ?: 2
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.media_preview_speed)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val speed = speeds[which]
                stateModel.pageStates.setPlayback(
                    currentIndex,
                    stateModel.pageStates.playbackFor(currentIndex).copy(speed = speed)
                )
                player?.setPlaybackSpeed(speed)
                dialog.dismiss()
            }
            .show()
    }

    private fun updatePagerInputForCurrentPage() {
        binding.mediaPager.isUserInputEnabled =
            currentTarget() != InternalOpenPolicy.Target.IMAGE_PREVIEW ||
                pagerAdapter.pageAt(currentIndex)?.isAtBaseZoom() != false
    }

    private fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        binding.toolbar.isVisible = !fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
        val controller = WindowCompat.getInsetsController(window, binding.root)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun shareCurrent() {
        val legacyPath = legacyPathOrNull(currentItem) ?: run {
            showToast(R.string.media_preview_unavailable)
            return
        }
        startActivitySafe(
            legacyPath.fileProviderUri
                .createSendStreamIntent(currentItem.mimeType)
                .withChooser(getString(R.string.media_preview_share))
        )
    }

    private fun openExternally(item: MediaPreviewItem) {
        val legacyPath = legacyPathOrNull(item) ?: run {
            showToast(R.string.media_preview_unavailable)
            return
        }
        startActivitySafe(
            legacyPath.fileProviderUri
                .createViewIntent(item.mimeType)
                .withChooser(getString(R.string.media_preview_open_with))
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
        pagerAdapter.pageAt(requestedIndex)?.setLoading(true)
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
            pagerAdapter.pageAt(requestedIndex)?.setLoading(false)
            if (loaded == null) {
                if (currentIndex == requestedIndex) {
                    showToast(R.string.media_preview_properties_failed)
                }
            } else {
                items[requestedIndex] = requestedItem.copy(fileItem = loaded)
                if (
                    currentIndex == requestedIndex &&
                    !isFinishing &&
                    lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                ) {
                    FilePropertiesDialogFragment.show(loaded, this@MediaPreviewActivity)
                }
            }
        }
    }

    private fun legacyPathOrNull(item: MediaPreviewItem) =
        try {
            item.path.toLegacyPathOrNull()
        } catch (_: RuntimeException) {
            null
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

    companion object {
        private const val STATE_INDEX = "media_preview.index"
        private const val STATE_ROTATION = "media_preview.rotation"
        private const val STATE_PLAYBACK_POSITION = "media_preview.playback_position"
        private const val STATE_PLAY_WHEN_READY = "media_preview.play_when_ready"
        private const val STATE_PLAYBACK_SPEED = "media_preview.playback_speed"
        private const val STATE_FULLSCREEN = "media_preview.fullscreen"
    }
}
