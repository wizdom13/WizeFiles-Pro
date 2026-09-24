package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDualPaneIntegrationSourceTest {
    @Test
    fun workspaceUsesRetainedPaneStateAndExistingTransferService() {
        val activity = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivity.kt"
        )
        val fragment = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val activityCoordinators = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivityCoordinators.kt"
        )
        val controller = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserTabsController.kt"
        )
        val workspace = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListWorkspaceController.kt"
        )
        val paneLayout = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserPaneLayout.kt"
        )

        assertTrue(activity.contains("windowStateController.widthDp >= MEDIUM_WIDTH_DP"))
        assertTrue(activity.contains("WindowInfoTracker.getOrCreate"))
        assertTrue(activity.contains("drawerCoordinator.persistentDrawerAllowed("))
        assertTrue(
            activityCoordinators.contains(
                "windowWidthDp >= persistentDrawerMinimumWidthDp"
            )
        )
        assertTrue(controller.contains("val secondaryPaneTag"))
        assertTrue(controller.contains("var activePane: BrowserPane"))
        assertTrue(fragment.contains("FileOperationService.copy(paths, target.viewModel.currentPath"))
        assertTrue(fragment.contains("FileOperationService.move(paths, target.viewModel.currentPath"))
        assertTrue(fragment.contains("detachSecondaryPane()"))
        assertTrue("BrowserPaneLayout(context)" in workspace)
        assertTrue("coordinator.updateVisibility" in workspace)
        assertTrue("workspaceController.updatePresentation" in fragment)
        assertTrue("workspaceController.autoScrollDuringDrag" in fragment)
        assertFalse("private fun ensureBreadcrumbRow" in fragment)
        assertTrue(paneLayout.contains("ViewGroup(context, attrs), AttachedBehavior"))
        assertTrue(paneLayout.contains("(primaryView as AttachedBehavior).behavior"))
        assertTrue(paneLayout.contains("fitsSystemWindows = view.fitsSystemWindows"))
        assertTrue(
            paneLayout.contains(
                "override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets = insets"
            )
        )

        val activeOutline = paneLayout.substringAfter("override fun dispatchDraw")
            .substringBefore("override fun onInterceptTouchEvent")
        assertTrue(activeOutline.contains("if (!secondaryVisible) return"))
    }

    private fun sourceFile(path: String): String {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(path),
            File(workingDirectory, path),
            workingDirectory.parentFile?.let { File(it, path) }
        ).filterNotNull()
            .first { it.isFile }
            .readText()
    }
}
