// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabsIntegrationSourceTest {
    @Test
    fun tabStripAndActionsRemainConnectedToBrowser() {
        val activity = File("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivity.kt")
            .readText()
        val fragment = File("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt")
            .readText()
        val appBar = File("src/main/res/layout/include_file_list_app_bar.xml").readText()
        val menu = File("src/main/res/menu/menu_file_list.xml").readText()

        assertTrue(activity.contains("BrowserTabsController"))
        assertTrue(activity.contains("detach(previousFragment)"))
        assertTrue(fragment.contains("bindTabStrip(this, binding.browserTabLayout)"))
        assertTrue(fragment.contains("}, viewLifecycleOwner, Lifecycle.State.RESUMED)"))
        assertTrue(fragment.contains("unbindTabStrip(binding.browserTabLayout)"))
        assertTrue(fragment.contains("updateTabTitle(this, selectedPath)"))
        assertTrue(activity.contains("navigationRoot?.takeIf { it.path == path }?.getName(this)"))
        assertTrue(fragment.contains("openNewTab(path.toAppPath())"))
        assertTrue(fragment.contains("findFragmentById(R.id.navigationFragment)"))
        assertTrue(fragment.contains("STATE_CURRENT_PATH"))
        assertTrue(appBar.contains("@+id/browserTabLayout"))
        assertTrue(menu.contains("@+id/action_new_tab"))
    }

}
