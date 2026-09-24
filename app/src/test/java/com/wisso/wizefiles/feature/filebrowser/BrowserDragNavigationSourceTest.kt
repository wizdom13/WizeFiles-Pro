package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDragNavigationSourceTest {
    @Test fun `breadcrumbs expose exact path drop targets`() {
        val breadcrumb = projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/BreadcrumbLayout.kt")
        assertTrue(breadcrumb.contains("listener.onBreadcrumbDrag(path, target, event)"))
        assertTrue(breadcrumb.contains("smoothScrollBy"))
    }

    @Test fun `tab hover navigates and tab drop never transfers`() {
        val activity = projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivity.kt")
        assertTrue(activity.contains("TAB_HOVER_DELAY_MILLIS = 600L"))
        assertTrue(activity.contains("selectTab(id)"))
        val dropBlock = activity.substringAfter("ACTION_DROP ->").substringBefore("ACTION_DRAG_ENDED")
        assertFalse(dropBlock.contains("FileOperationService"))
        assertTrue(dropBlock.contains("file_list_drag_drop_inside_pane"))
    }

    private fun projectFile(path: String): String =
        listOf(File(path), File("app/$path")).firstOrNull(File::exists)?.readText()
            ?: error("Missing $path")
}
