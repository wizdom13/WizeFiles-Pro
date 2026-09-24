// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.ebook

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import com.wisso.wizefiles.R
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.toUri

@OptIn(ExperimentalReadiumApi::class)
class EbookReaderFragment : Fragment(), EpubNavigatorFragment.Listener {
    private val model: EbookViewerViewModel by activityViewModels()
    private var navigator: EpubNavigatorFragment? = null
    private var fontSize = 1.0
    private var theme = Theme.LIGHT
    private val hardenWebViews = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        view?.hardenPublicationWebViews()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = model.currentSession()
        childFragmentManager.fragmentFactory = if (session != null) {
            session.navigatorFactory.createFragmentFactory(
                initialLocator = null,
                initialPreferences = EpubPreferences(fontSize = fontSize, theme = theme),
                listener = this,
                configuration = EpubNavigatorFragment.Configuration()
            )
        } else {
            EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        if (session == null) requireActivity().finish()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ebook_reader, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                add(R.id.ebookNavigatorContainer, EpubNavigatorFragment::class.java, Bundle(), TAG_NAVIGATOR)
            }
        }
        navigator = childFragmentManager.findFragmentByTag(TAG_NAVIGATOR) as? EpubNavigatorFragment
        view.viewTreeObserver.addOnGlobalLayoutListener(hardenWebViews)
        view.post { view.hardenPublicationWebViews() }
    }

    override fun onDestroyView() {
        view?.viewTreeObserver?.takeIf { it.isAlive }
            ?.removeOnGlobalLayoutListener(hardenWebViews)
        navigator = null
        super.onDestroyView()
    }

    fun goTo(link: Link): Boolean = navigator?.go(link) == true

    fun changeFontSize(delta: Double) {
        fontSize = (fontSize + delta).coerceIn(0.6, 2.0)
        submitPreferences()
    }

    fun cycleTheme() {
        theme = when (theme) {
            Theme.LIGHT -> Theme.SEPIA
            Theme.SEPIA -> Theme.DARK
            Theme.DARK -> Theme.LIGHT
        }
        submitPreferences()
    }

    private fun submitPreferences() {
        navigator?.submitPreferences(EpubPreferences(fontSize = fontSize, theme = theme))
    }

    private fun View.hardenPublicationWebViews() {
        if (this is WebView) {
            settings.blockNetworkLoads = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).hardenPublicationWebViews()
        }
    }

    override fun onResourceLoadFailed(href: Url, error: ReadError) {
        (activity as? Listener)?.onEbookResourceFailed()
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        (activity as? Listener)?.onEbookExternalLink(url.toUri())
    }

    interface Listener {
        fun onEbookExternalLink(uri: Uri)
        fun onEbookResourceFailed()
    }

    private companion object {
        const val TAG_NAVIGATOR = "ebook_navigator"
    }
}
