// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.pdfviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.core.files.model.loadFileItem
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.databinding.ActivityPdfViewerBinding
import com.wisso.wizefiles.feature.details.FilePropertiesDialogFragment
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.createSendStreamIntent
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.withChooser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfViewerActivity : BaseThemedActivity(), WizePdfViewerFragment.Listener {
    private lateinit var binding: ActivityPdfViewerBinding
    private lateinit var documentPath: AppPath
    private var documentMimeType = MimeType(PDF_MIME_TYPE)
    private var viewerFragment: WizePdfViewerFragment? = null
    private var isFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.extraPath ?: run {
            finish()
            return
        }
        documentPath = path
        documentMimeType = intent.type?.asMimeTypeOrNull() ?: MimeType(PDF_MIME_TYPE)
        isFullscreen = savedInstanceState?.getBoolean(STATE_FULLSCREEN) ?: false

        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = documentPath.name

        binding.retryButton.setOnClickListener { loadDocument(replaceFragment = true) }
        binding.openWithButton.setOnClickListener { openExternally() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        viewerFragment?.closeSearch() == true -> invalidateOptionsMenu()
                        isFullscreen -> setFullscreen(false)
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        )
        loadDocument(replaceFragment = false)
        if (isFullscreen) setFullscreen(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_pdf_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_pdf_search -> {
                viewerFragment?.toggleSearch()
                true
            }
            R.id.action_pdf_fullscreen -> {
                setFullscreen(!isFullscreen)
                true
            }
            R.id.action_pdf_share -> {
                shareDocument()
                true
            }
            R.id.action_pdf_properties -> {
                showProperties()
                true
            }
            R.id.action_pdf_open_with -> {
                openExternally()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_FULLSCREEN, isFullscreen)
        super.onSaveInstanceState(outState)
    }

    override fun onPdfDocumentReady(pageCount: Int) {
        binding.loadingProgress.isVisible = false
        binding.errorPanel.isVisible = false
        supportActionBar?.subtitle = resources.getQuantityString(
            R.plurals.pdf_viewer_page_count,
            pageCount,
            pageCount
        )
    }

    override fun onPdfDocumentError(error: Throwable) {
        showError(R.string.pdf_viewer_failed)
    }

    override fun onPdfExternalLink(uri: Uri) {
        startActivitySafe(
            Intent(Intent.ACTION_VIEW, uri)
                .withChooser(getString(R.string.pdf_viewer_open_link))
        )
    }

    override fun onPdfLinkBlocked(uri: Uri) {
        showToast(R.string.pdf_viewer_link_blocked)
    }

    override fun onPdfImmersiveModeRequested(enterImmersive: Boolean) {
        setFullscreen(enterImmersive)
    }

    private fun loadDocument(replaceFragment: Boolean) {
        val legacyPath = legacyPathOrNull()
        if (legacyPath == null) {
            showError(R.string.pdf_viewer_unavailable)
            return
        }
        binding.loadingProgress.isVisible = true
        binding.errorPanel.isVisible = false
        supportActionBar?.subtitle = null
        try {
            val existing = if (replaceFragment) null else {
                supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
                    as? WizePdfViewerFragment
            }
            val fragment = existing ?: WizePdfViewerFragment().also {
                supportFragmentManager.commitNow {
                    replace(R.id.pdfFragmentContainer, it, FRAGMENT_TAG)
                }
            }
            viewerFragment = fragment
            val uri = legacyPath.fileProviderUri
            if (fragment.documentUri != uri) fragment.documentUri = uri
        } catch (_: UnsupportedOperationException) {
            showError(R.string.pdf_viewer_unsupported_device)
        } catch (_: RuntimeException) {
            showError(R.string.pdf_viewer_failed)
        }
    }

    private fun showError(messageRes: Int) {
        binding.loadingProgress.isVisible = false
        binding.errorText.setText(messageRes)
        binding.errorPanel.isVisible = true
    }

    private fun shareDocument() {
        val legacyPath = legacyPathOrNull() ?: run {
            showToast(R.string.pdf_viewer_unavailable)
            return
        }
        startActivitySafe(
            legacyPath.fileProviderUri
                .createSendStreamIntent(documentMimeType)
                .withChooser(getString(R.string.pdf_viewer_share))
        )
    }

    private fun openExternally() {
        val legacyPath = legacyPathOrNull() ?: run {
            showToast(R.string.pdf_viewer_unavailable)
            return
        }
        startActivitySafe(
            legacyPath.fileProviderUri
                .createViewIntent(documentMimeType)
                .withChooser(getString(R.string.pdf_viewer_open_with))
        )
    }

    private fun showProperties() {
        binding.loadingProgress.isVisible = true
        lifecycleScope.launch {
            val fileItem = withContext(Dispatchers.IO) {
                try {
                    documentPath.loadFileItem()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: IOException) {
                    null
                } catch (_: RuntimeException) {
                    null
                }
            }
            binding.loadingProgress.isVisible = false
            if (fileItem == null) {
                showToast(R.string.pdf_viewer_properties_failed)
            } else if (
                !isFinishing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                FilePropertiesDialogFragment.show(fileItem, this@PdfViewerActivity)
            }
        }
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
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun legacyPathOrNull() =
        try {
            documentPath.toLegacyPathOrNull()
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

    private companion object {
        const val FRAGMENT_TAG = "wize_pdf_viewer"
        const val PDF_MIME_TYPE = "application/pdf"
        const val STATE_FULLSCREEN = "pdf_viewer.fullscreen"
    }
}
