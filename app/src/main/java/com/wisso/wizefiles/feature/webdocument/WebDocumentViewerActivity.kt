// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.webdocument

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.databinding.ActivityWebDocumentViewerBinding
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.createSendStreamIntent
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.withChooser
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import kotlinx.coroutines.launch

class WebDocumentViewerActivity : BaseThemedActivity() {
    private lateinit var binding: ActivityWebDocumentViewerBinding
    private lateinit var documentPath: AppPath
    private var documentMimeType = MimeType("text/html")
    private val model: WebDocumentViewerViewModel by viewModels()
    private var activeBundle: SavedWebDocumentBundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documentPath = intent.extraPath ?: run { finish(); return }
        documentMimeType = intent.type?.asMimeTypeOrNull() ?: documentMimeType
        binding = ActivityWebDocumentViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = documentPath.name
        configureWebView(binding.webView)
        binding.retryButton.setOnClickListener { model.retry(documentPath, documentMimeType) }
        binding.openWithButton.setOnClickListener { openExternally() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
        model.load(documentPath, documentMimeType)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_web_document_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
        R.id.action_web_document_reload -> { activeBundle?.let { binding.webView.loadUrl(it.startUrl) }; true }
        R.id.action_web_document_share -> { shareDocument(); true }
        R.id.action_web_document_open_with -> { openExternally(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun render(state: WebDocumentViewerViewModel.State) {
        binding.loadingProgress.isVisible = state is WebDocumentViewerViewModel.State.Loading
        binding.errorPanel.isVisible = state is WebDocumentViewerViewModel.State.Error
        binding.webView.isVisible = state is WebDocumentViewerViewModel.State.Ready
        if (state is WebDocumentViewerViewModel.State.Error) binding.errorText.setText(state.failure.messageResource())
        if (state is WebDocumentViewerViewModel.State.Ready && activeBundle !== state.session.bundle) {
            activeBundle = state.session.bundle
            binding.webView.loadUrl(state.session.bundle.startUrl)
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        webView.settings.apply {
            javaScriptEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        webView.webViewClient = OfflineSavedDocumentClient()
    }

    private inner class OfflineSavedDocumentClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            val resource = activeBundle?.resourceFor(request.url.toString())
            if (resource != null) {
                return WebResourceResponse(resource.mimeType, resource.charset, 200, "OK", SECURITY_HEADERS, FileInputStream(resource.file))
            }
            return WebResourceResponse("text/plain", "utf-8", 404, "Blocked", SECURITY_HEADERS, ByteArrayInputStream(ByteArray(0)))
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (activeBundle?.contains(request.url.toString()) == true) return false
            if (request.isForMainFrame && request.hasGesture()) openExternalLink(request.url)
            return true
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            binding.webView.isVisible = false
            binding.errorPanel.isVisible = true
            binding.errorText.setText(R.string.web_document_viewer_renderer_failed)
            return true
        }
    }

    private fun openExternalLink(uri: Uri) {
        if (!WebDocumentLinkPolicy.allowsScheme(uri.scheme)) { showToast(R.string.web_document_viewer_link_blocked); return }
        startActivitySafe(Intent(Intent.ACTION_VIEW, uri).withChooser(getString(R.string.web_document_viewer_open_link)))
    }

    private fun shareDocument() {
        val path = documentPath.toLegacyPathOrNull() ?: run { showToast(R.string.web_document_viewer_unavailable); return }
        startActivitySafe(path.fileProviderUri.createSendStreamIntent(documentMimeType).withChooser(getString(R.string.web_document_viewer_share)))
    }

    private fun openExternally() {
        val path = documentPath.toLegacyPathOrNull() ?: run { showToast(R.string.web_document_viewer_unavailable); return }
        startActivitySafe(path.fileProviderUri.createViewIntent(documentMimeType).withChooser(getString(R.string.web_document_viewer_open_with)))
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.webViewClient = WebViewClient()
        binding.webView.removeAllViews()
        binding.webView.destroy()
        super.onDestroy()
    }

    private fun WebDocumentViewerViewModel.Failure.messageResource(): Int = when (this) {
        WebDocumentViewerViewModel.Failure.UNSAFE_OR_DAMAGED -> R.string.web_document_viewer_unsafe
        WebDocumentViewerViewModel.Failure.UNAVAILABLE -> R.string.web_document_viewer_unavailable
        WebDocumentViewerViewModel.Failure.OPEN_FAILED -> R.string.web_document_viewer_failed
    }

    private fun applySystemBarInsets() {
        val root = binding.root
        val initial = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(initial[0] + bars.left, initial[1] + bars.top, initial[2] + bars.right, initial[3] + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private companion object {
        val SECURITY_HEADERS = mapOf(
            "Content-Security-Policy" to "default-src 'none'; img-src data: http: https: cid:; style-src 'unsafe-inline' http: https:; font-src http: https:; media-src http: https:; script-src 'none'; object-src 'none'; frame-src 'none'; connect-src 'none'; form-action 'none'",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer"
        )
    }
}

object WebDocumentLinkPolicy {
    fun allowsScheme(scheme: String?): Boolean = setOf("http", "https", "mailto").any { it.equals(scheme, true) }
}
