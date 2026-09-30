// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about.changelog

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.databinding.ActivityChangelogBinding
import com.wisso.wizefiles.util.startActivitySafe
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.R as MaterialR

class ChangelogActivity : BaseThemedActivity() {
    private lateinit var binding: ActivityChangelogBinding
    private var webView: WebView? = null
    private var content: View? = null
    private var initialScroll = 0
    private var contentLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityChangelogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.changelog_title)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        initialScroll = savedInstanceState?.getInt(SCROLL) ?: 0
        lifecycleScope.launch {
            try {
                val html = withContext(Dispatchers.IO) {
                    assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() }
                }
                showContent(html)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(this@ChangelogActivity, R.string.changelog_load_failed, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showContent(html: String) {
        val viewer = runCatching { WebView(this) }.getOrNull()
        if (viewer == null) {
            val text = changelogTextView(this, html, false)
            content = text
            binding.content.addView(text)
            text.post { text.scrollTo(0, initialScroll); contentLoaded = true }
            return
        }
        webView = viewer
        content = viewer
        viewer.setBackgroundColor(Color.TRANSPARENT)
        viewer.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            textZoom = (100 * resources.configuration.fontScale).toInt()
        }
        viewer.webViewClient = object : WebViewClient() {
            @Deprecated("Compatibility overload")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = openLink(url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = openLink(request.url.toString())
            override fun onPageFinished(view: WebView, url: String) {
                if (!contentLoaded) view.post {
                    view.scrollTo(0, initialScroll)
                    contentLoaded = true
                }
            }
        }
        binding.content.addView(viewer)
        fun color(attribute: Int) = String.format(Locale.ROOT, "#%06X", MaterialColors.getColor(binding.root, attribute) and 0xffffff)
        val theme = """
            <style>:root {
              --background:${color(MaterialR.attr.colorSurface)};
              --card:${color(MaterialR.attr.colorSurfaceContainerLow)};
              --text:${color(MaterialR.attr.colorOnSurface)};
              --muted:${color(MaterialR.attr.colorOnSurfaceVariant)};
              --primary:${color(MaterialR.attr.colorPrimary)};
              --outline:${color(MaterialR.attr.colorOutlineVariant)};
            }</style>
        """.trimIndent()
        viewer.loadDataWithBaseURL(BASE_URL, html.replace("</head>", "$theme</head>"), "text/html", "UTF-8", null)
    }

    private fun openLink(url: String): Boolean {
        if (url.startsWith("#") || url.startsWith("$BASE_URL#")) return false
        val uri = Uri.parse(url)
        if (uri.scheme in listOf("https", "http")) startActivitySafe(Intent(Intent.ACTION_VIEW, uri))
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SCROLL, if (contentLoaded) content?.scrollY ?: 0 else initialScroll)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        webView?.let {
            binding.content.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        content = null
        super.onDestroy()
    }

    companion object {
        private const val BASE_URL = "https://appassets.androidplatform.net/assets/changelogs.html"
        private const val SCROLL = "changelog-scroll"
    }
}
