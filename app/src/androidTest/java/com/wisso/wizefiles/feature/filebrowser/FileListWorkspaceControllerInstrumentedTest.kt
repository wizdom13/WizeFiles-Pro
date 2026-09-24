// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wisso.wizefiles.settings.SettingsActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileListWorkspaceControllerInstrumentedTest {
    @Test
    fun dualPanePresentationBindsAndReleasesRealViews() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val breadcrumb = BreadcrumbLayout(activity)
                val content = FrameLayout(activity)
                val recycler = RecyclerView(activity)
                content.addView(
                    recycler,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                root.addView(
                    breadcrumb,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                root.addView(
                    content,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                )
                activity.setContentView(root)

                val controller = FileListWorkspaceController()
                controller.bind(
                    context = activity,
                    views = FileListWorkspaceController.Views(
                        contentLayout = content,
                        primaryBreadcrumb = breadcrumb,
                        persistentDrawerLayout = null,
                        recyclerView = recycler
                    ),
                    onDividerFractionChanged = {},
                    onSecondaryPaneTouched = {}
                )
                controller.ensureLayout()

                val change = controller.updatePresentation(
                    dualPaneVisible = true,
                    requestedActivePane = BrowserPane.PRIMARY,
                    dividerFraction = 0.5f,
                    verticalHingeBounds = null,
                    persistentDrawerAllowed = true
                )

                assertTrue(change.secondaryPaneRequired)
                assertNotNull(controller.paneLayout)
                assertTrue(controller.paneLayout?.secondaryVisible == true)
                assertTrue(controller.secondaryBreadcrumb?.isVisible == true)
                assertSame(controller.paneLayout, root.getChildAt(1))

                controller.updatePresentation(
                    dualPaneVisible = false,
                    requestedActivePane = BrowserPane.SECONDARY,
                    dividerFraction = 0.5f,
                    verticalHingeBounds = null,
                    persistentDrawerAllowed = true
                )
                assertFalse(controller.dualPaneVisible)
                assertTrue(controller.activePane == BrowserPane.PRIMARY)

                controller.release()
                assertNull(controller.paneLayout)
                assertNull(controller.secondaryBreadcrumb)
            }
        }
    }
}
