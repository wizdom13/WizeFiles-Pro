package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDragIntegrationSourceTest {
    @Test fun `drag payload is private opaque and activity scoped`() {
        val drag = projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserDragCoordinator.kt")
        assertTrue(drag.contains("application/vnd.wizefiles.internal-drag"))
        assertTrue(drag.contains("ClipData.Item(id)"))
        assertFalse(drag.contains("DRAG_FLAG_GLOBAL"))
        assertFalse(drag.contains("content://"))
        assertTrue(drag.contains("FileOperationService.copy"))
        assertTrue(drag.contains("FileOperationService.move"))
    }

    @Test fun `tabs navigate on hover but never execute a drop`() {
        val activity = projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivity.kt")
        assertTrue(activity.contains("TAB_HOVER_DELAY_MILLIS = 600L"))
        val dropBlock = activity.substringAfter("ACTION_DROP ->").substringBefore("ACTION_DRAG_ENDED")
        assertFalse(dropBlock.contains("FileOperationService"))
        assertTrue(dropBlock.contains("file_list_drag_drop_inside_pane"))
    }

    @Test fun `no permission or third party drag dependency is introduced`() {
        val manifest = projectFile("src/main/AndroidManifest.xml")
        val gradle = projectFile("build.gradle")
        assertFalse(manifest.contains("DRAG_AND_DROP"))
        assertFalse(gradle.contains("draglistview", ignoreCase = true))
    }

    private fun projectFile(path: String): String =
        listOf(File(path), File("app/$path")).firstOrNull(File::exists)?.readText()
            ?: error("Missing $path")
}
