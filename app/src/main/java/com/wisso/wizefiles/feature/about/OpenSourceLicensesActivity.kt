package com.wisso.wizefiles.feature.about

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.databinding.ActivityOpenSourceLicensesBinding
import com.wisso.wizefiles.util.createIntent
import java.nio.charset.StandardCharsets
import java.util.Locale

class OpenSourceLicensesActivity : BaseThemedActivity() {
    private lateinit var binding: ActivityOpenSourceLicensesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOpenSourceLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_licenses_title)

        val pageBackground = resolveThemeColor(
            MaterialR.attr.colorSurface,
            Color.rgb(0xF7, 0xF2, 0xFA)
        )
        binding.webView.configureForStaticLocalContent(pageBackground)
        val html = resources.openRawResource(R.raw.open_libs)
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
            .withMaterialTheme()
        binding.webView.loadDataWithBaseURL(
            LOCAL_CONTENT_BASE_URL,
            html,
            "text/html",
            StandardCharsets.UTF_8.name(),
            null
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun String.withMaterialTheme(): String {
        val themeOverrides = """
            <style id="wizefiles-material-theme">
            :root {
              --bg:${themeColor(MaterialR.attr.colorSurface, Color.rgb(0xF7, 0xF2, 0xFA))};
              --surface:${themeColor(MaterialR.attr.colorSurfaceContainerLow, Color.rgb(0xFF, 0xFB, 0xFE))};
              --surface2:${themeColor(MaterialR.attr.colorSurfaceContainer, Color.rgb(0xEC, 0xE6, 0xF0))};
              --outline:${themeColor(MaterialR.attr.colorOutline, Color.rgb(0x79, 0x74, 0x7E))};
              --text:${themeColor(MaterialR.attr.colorOnSurface, Color.rgb(0x1D, 0x1B, 0x20))};
              --muted:${themeColor(MaterialR.attr.colorOnSurfaceVariant, Color.rgb(0x49, 0x45, 0x4F))};
              --chip-bg:${themeColor(MaterialR.attr.colorSecondaryContainer, Color.rgb(0xE8, 0xDE, 0xFF))};
              --chip-text:${themeColor(MaterialR.attr.colorOnSecondaryContainer, Color.rgb(0x1D, 0x19, 0x2B))};
              --link:${themeColor(MaterialR.attr.colorPrimary, Color.rgb(0x67, 0x50, 0xA4))};
            }
            </style>
        """.trimIndent()
        return replaceFirst("</head>", "$themeOverrides\n</head>", ignoreCase = true)
    }

    private fun themeColor(@AttrRes attribute: Int, @ColorInt fallback: Int): String =
        String.format(Locale.ROOT, "#%06X", resolveThemeColor(attribute, fallback) and 0xFFFFFF)

    @ColorInt
    private fun resolveThemeColor(@AttrRes attribute: Int, @ColorInt fallback: Int): Int =
        MaterialColors.getColor(binding.root, attribute, fallback)

    @Suppress("DEPRECATION")
    private fun WebView.configureForStaticLocalContent(@ColorInt backgroundColor: Int) {
        settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            databaseEnabled = false
            allowContentAccess = false
            allowFileAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            blockNetworkLoads = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        setBackgroundColor(backgroundColor)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = true

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
        }
    }

    companion object {
        private const val LOCAL_CONTENT_BASE_URL = "https://wizefiles.local/"

        fun createIntent() = OpenSourceLicensesActivity::class.createIntent()
    }
}
