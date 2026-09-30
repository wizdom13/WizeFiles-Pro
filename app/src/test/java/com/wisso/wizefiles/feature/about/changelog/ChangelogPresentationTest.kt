// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about.changelog

import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.R
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class ChangelogPresentationTest {
    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var activity: FragmentActivity

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("changelog", Context.MODE_PRIVATE).edit().clear().commit()
        controller = Robolectric.buildActivity(FragmentActivity::class.java)
        activity = controller.get()
        activity.setTheme(R.style.Theme_WizeFiles)
        controller.setup()
    }

    @After fun tearDown() {
        controller.pause().stop().destroy()
    }

    private fun show(): ChangelogDialogFragment = ChangelogDialogFragment.newInstance(
        ChangelogNotice("1.1.0", 14, "<h2>1.1.0</h2><ul><li>Offline release notes</li></ul>")
    ).also { it.showNow(activity.supportFragmentManager, ChangelogDialogFragment.TAG) }

    @Test fun closeAcknowledgesTheReleaseAndAnOlderNoticeCannotRegressIt() {
        val dialog = show().requireDialog() as AlertDialog
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val preferences = ChangelogPreferences(activity)
        assertEquals(14, preferences.lastSeenOrder)
        preferences.acknowledge(12)
        assertEquals(14, ChangelogPreferences(activity).lastSeenOrder)
    }

    @Test fun backOrOutsideCancellationAcknowledgesTheRelease() {
        show().requireDialog().cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(14, ChangelogPreferences(activity).lastSeenOrder)
    }

    @Test fun fullChangelogOpensTheOfflineActivityAndAcknowledges() {
        val dialog = show().requireDialog() as AlertDialog
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ChangelogActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertEquals(14, ChangelogPreferences(activity).lastSeenOrder)
    }

    @Test fun recreationRestoresOneDialogWithoutAcknowledgingIt() {
        show()
        controller.recreate()
        activity = controller.get()
        assertEquals(0, ChangelogPreferences(activity).lastSeenOrder)
        val dialogs = activity.supportFragmentManager.fragments.filterIsInstance<ChangelogDialogFragment>()
        assertEquals(1, dialogs.size)
        assertTrue(dialogs.single().requireDialog().isShowing)
    }
}
