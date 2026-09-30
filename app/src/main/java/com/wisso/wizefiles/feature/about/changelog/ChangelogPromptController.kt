// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about.changelog

import android.content.Intent
import android.util.Log
import android.view.ViewTreeObserver
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.util.extraPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Attached only after browser unlock/initialization; never interrupts external file flows. */
internal class ChangelogPromptController(private val activity: FragmentActivity) : DefaultLifecycleObserver {
    private var loadingStarted = false
    private var pending: ChangelogNotice? = null
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
        if (focused) activity.window.decorView.post { maybeShow() }
    }

    init {
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        activity.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        activity.window.decorView.post { maybeShow() }
    }

    private fun maybeShow() {
        val fragments = activity.supportFragmentManager
        val intent = activity.intent
        if (activity.isFinishing || activity.isDestroyed || fragments.isStateSaved ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !activity.hasWindowFocus() || fragments.findFragmentByTag(ChangelogDialogFragment.TAG) != null ||
            intent.action !in listOf(null, Intent.ACTION_MAIN) || intent.data != null ||
            intent.clipData != null || intent.extraPath != null
        ) return

        if (!loadingStarted) {
            loadingStarted = true
            val context = activity.applicationContext
            activity.lifecycleScope.launch {
                try {
                    pending = withContext(Dispatchers.IO) {
                        val html = context.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() }
                        ChangelogCatalog.parse(html).notice(BuildConfig.VERSION_NAME, ChangelogPreferences(context).lastSeenOrder)
                    }
                    maybeShow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("Changelog", "Could not read bundled release notes", error)
                }
            }
            return
        }
        val notice = pending ?: return
        pending = null
        if (ChangelogPreferences(activity).lastSeenOrder < notice.order) {
            ChangelogDialogFragment.newInstance(notice).showNow(fragments, ChangelogDialogFragment.TAG)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        pending = null
    }
}
