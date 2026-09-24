// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.mediapreview

import android.graphics.Bitmap
import android.graphics.PointF
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.databinding.ItemMediaPreviewPageBinding
import com.wisso.wizefiles.feature.advancedformats.image.AdvancedImagePreviewDecoder
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class MediaPreviewPagerAdapter(
    private val items: List<MediaPreviewItem>,
    private val rotationFor: (Int) -> Float,
    private val onOpenExternally: (MediaPreviewItem) -> Unit,
    private val onPageBound: (Int) -> Unit,
    private val onZoomStateChanged: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<MediaPreviewPagerAdapter.PageViewHolder>() {
    private val boundPages = mutableMapOf<Int, PageViewHolder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder =
        PageViewHolder(
            ItemMediaPreviewPageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onOpenExternally,
            onZoomStateChanged
        )

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        boundPages.entries.removeAll { it.value === holder }
        boundPages[position] = holder
        holder.bind(position, items[position], rotationFor(position))
        onPageBound(position)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        boundPages.entries.removeAll { it.value === holder }
        holder.recycle()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size

    fun pageAt(position: Int): PageViewHolder? = boundPages[position]

    fun setRotation(position: Int, rotation: Float) {
        boundPages[position]?.setRotation(rotation)
    }

    fun recycleAll() {
        boundPages.values.toSet().forEach(PageViewHolder::recycle)
        boundPages.clear()
    }

    class PageViewHolder(
        private val binding: ItemMediaPreviewPageBinding,
        private val onOpenExternally: (MediaPreviewItem) -> Unit,
        private val onZoomStateChanged: (Int, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        val playerView
            get() = binding.videoPlayer

        private var renderGeneration = 0
        private var boundPosition = RecyclerView.NO_POSITION
        private var boundItem: MediaPreviewItem? = null
        private var advancedDecodeJob: Job? = null
        private var advancedBitmap: Bitmap? = null

        init {
            binding.openExternallyButton.setOnClickListener {
                boundItem?.let(onOpenExternally)
            }
        }

        fun bind(position: Int, item: MediaPreviewItem, rotation: Float) {
            val generation = ++renderGeneration
            boundPosition = position
            boundItem = item
            resetViews()
            setRotation(rotation)
            val legacyPath = try {
                item.path.toLegacyPathOrNull()
            } catch (_: RuntimeException) {
                null
            }
            if (legacyPath == null) {
                showError(R.string.media_preview_unavailable)
                return
            }
            when (
                InternalOpenPolicy.targetFor(
                    item.mimeType,
                    legacyPath.isArchivePath,
                    item.userFacingName
                )
            ) {
                InternalOpenPolicy.Target.VIDEO_PREVIEW -> {
                    binding.loadingProgress.isVisible = false
                    binding.videoPlayer.isVisible = true
                }
                InternalOpenPolicy.Target.IMAGE_PREVIEW -> {
                    val uri = legacyPath.fileProviderUri
                    when {
                        AdvancedImagePreviewDecoder.supports(item.userFacingName) ->
                            decodeAdvancedImage(legacyPath, item.userFacingName, generation)
                        usesAnimatedImagePipeline(item) -> {
                            binding.animatedImage.isVisible = true
                            binding.animatedImage.setOnScaleChangeListener { _, _, _ ->
                                notifyZoomState()
                            }
                            binding.animatedImage.load(uri) {
                                listener(
                                    onSuccess = { _, _ ->
                                        if (generation == renderGeneration) {
                                            binding.animatedImage.setScale(
                                                binding.animatedImage.minimumScale,
                                                false
                                            )
                                            setLoading(false)
                                            notifyZoomState()
                                        }
                                    },
                                    onError = { _, _ ->
                                        if (generation == renderGeneration) {
                                            showError(R.string.media_preview_decode_failed)
                                        }
                                    }
                                )
                            }
                        }
                        else -> {
                            binding.staticImage.isVisible = true
                            binding.staticImage.orientation =
                                SubsamplingScaleImageView.ORIENTATION_USE_EXIF
                            binding.staticImage.setOnImageEventListener(
                                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                                    override fun onReady() {
                                        if (generation == renderGeneration) {
                                            setLoading(false)
                                            notifyZoomState()
                                        }
                                    }

                                    override fun onImageLoadError(exception: Exception) {
                                        if (generation == renderGeneration) {
                                            showError(R.string.media_preview_decode_failed)
                                        }
                                    }
                                }
                            )
                            binding.staticImage.setOnStateChangedListener(
                                object : SubsamplingScaleImageView.OnStateChangedListener {
                                    override fun onScaleChanged(newScale: Float, origin: Int) {
                                        notifyZoomState()
                                    }

                                    override fun onCenterChanged(newCenter: PointF?, origin: Int) = Unit
                                }
                            )
                            binding.staticImage.setImage(ImageSource.uri(uri))
                        }
                    }
                }
                InternalOpenPolicy.Target.AUDIO_PLAYER,
                InternalOpenPolicy.Target.PDF_VIEWER,
                InternalOpenPolicy.Target.EBOOK_VIEWER,
                InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER,
                InternalOpenPolicy.Target.FONT_VIEWER,
                InternalOpenPolicy.Target.CONTAINER_BROWSER,
                InternalOpenPolicy.Target.EXTERNAL_APP -> {
                    showError(R.string.media_preview_unavailable)
                }
            }
        }

        fun setRotation(rotation: Float) {
            binding.staticImage.rotation = rotation
            binding.animatedImage.rotation = rotation
        }

        fun isAtBaseZoom(): Boolean =
            when {
                binding.staticImage.isVisible ->
                    binding.staticImage.scale <=
                        binding.staticImage.minScale * BASE_ZOOM_TOLERANCE
                binding.animatedImage.isVisible ->
                    binding.animatedImage.scale <=
                        binding.animatedImage.minimumScale * BASE_ZOOM_TOLERANCE
                else -> true
            }

        fun setLoading(loading: Boolean) {
            binding.loadingProgress.isVisible = loading
        }

        fun showError(messageRes: Int) {
            renderGeneration++
            clearAdvancedDecode()
            binding.loadingProgress.isVisible = false
            binding.staticImage.isVisible = false
            binding.animatedImage.isVisible = false
            binding.videoPlayer.player = null
            binding.videoPlayer.isVisible = false
            binding.errorText.setText(messageRes)
            binding.errorPanel.isVisible = true
        }

        fun recycle() {
            renderGeneration++
            clearAdvancedDecode()
            binding.staticImage.recycle()
            binding.animatedImage.setImageDrawable(null)
            binding.videoPlayer.player = null
            boundItem = null
            boundPosition = RecyclerView.NO_POSITION
        }

        private fun resetViews() {
            clearAdvancedDecode()
            binding.staticImage.setOnStateChangedListener(null)
            binding.staticImage.recycle()
            binding.staticImage.isVisible = false
            binding.animatedImage.setImageDrawable(null)
            binding.animatedImage.setOnScaleChangeListener(null)
            binding.animatedImage.isVisible = false
            binding.videoPlayer.player = null
            binding.videoPlayer.isVisible = false
            binding.errorPanel.isVisible = false
            binding.loadingProgress.isVisible = true
        }

        private fun decodeAdvancedImage(path: Path, fileName: String, generation: Int) {
            val lifecycleOwner = binding.root.findViewTreeLifecycleOwner()
            if (lifecycleOwner == null) {
                showError(R.string.media_preview_unavailable)
                return
            }
            binding.animatedImage.isVisible = true
            binding.animatedImage.setOnScaleChangeListener { _, _, _ -> notifyZoomState() }
            advancedDecodeJob = lifecycleOwner.lifecycleScope.launch {
                var decoded: Bitmap? = null
                try {
                    withContext(Dispatchers.IO) {
                        decoded = AdvancedImagePreviewDecoder.decode(path, fileName)
                    }
                    val bitmap = decoded ?: return@launch
                    if (generation != renderGeneration) return@launch
                    advancedBitmap = bitmap
                    decoded = null
                    binding.animatedImage.setImageBitmap(bitmap)
                    binding.animatedImage.setScale(
                        binding.animatedImage.minimumScale,
                        false
                    )
                    setLoading(false)
                    notifyZoomState()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    if (generation == renderGeneration) {
                        showError(R.string.media_preview_decode_failed)
                    }
                } finally {
                    decoded?.recycle()
                }
            }
        }

        private fun clearAdvancedDecode() {
            advancedDecodeJob?.cancel()
            advancedDecodeJob = null
            binding.animatedImage.setImageDrawable(null)
            advancedBitmap?.recycle()
            advancedBitmap = null
        }

        private fun notifyZoomState() {
            val position = boundPosition
            if (position != RecyclerView.NO_POSITION) {
                onZoomStateChanged(position, isAtBaseZoom())
            }
        }

        private fun usesAnimatedImagePipeline(item: MediaPreviewItem): Boolean =
            item.mimeType.subtype.equals("gif", ignoreCase = true) ||
                item.mimeType.subtype.equals("webp", ignoreCase = true) ||
                item.mimeType.subtype.contains("svg", ignoreCase = true)

        private companion object {
            const val BASE_ZOOM_TOLERANCE = 1.05f
        }
    }
}
