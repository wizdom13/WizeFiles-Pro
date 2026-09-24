package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListArchitectureBoundarySourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `fragment keeps extracted responsibilities behind controllers`() {
        val fragment = source("FileListFragment.kt")

        listOf(
            "FileListBottomPanelController",
            "FileListOperationController",
            "FileListExternalActionController",
            "FileListWorkspaceController",
            "FileListSearchController",
            "FileListRenderCoordinator",
            "BrowserCommandCoordinator",
            "BrowserSelectionCoordinator",
            "BrowserNavigationCoordinator",
            "BrowserOperationLauncher",
            "BrowserStateRestorer"
        ).forEach { assertTrue("Missing $it delegation", it in fragment) }

        listOf(
            "import com.wisso.wizefiles.core.app.clipboardManager",
            "import com.wisso.wizefiles.feature.internalviewer.InternalOpenIntents",
            "import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy",
            "import androidx.core.content.pm.ShortcutManagerCompat",
            "import com.wisso.wizefiles.terminal.TerminalLauncher",
            "import com.wisso.wizefiles.navigation.BookmarkDirectories",
            "private var paneLayout",
            "private var secondaryBreadcrumb",
            "private val workspaceCoordinator"
        ).forEach { assertFalse("Responsibility leaked into FileListFragment: $it", it in fragment) }

        assertTrue(
            "FileListFragment grew beyond the post-extraction review budget",
            fragment.lineSequence().count() <= MAX_FRAGMENT_LINES
        )
    }

    @Test
    fun `view lifecycle releases every view bound collaborator`() {
        val fragment = source("FileListFragment.kt")
        val teardown = fragment.substringAfter("override fun onDestroyView()")
            .substringBefore("private fun refresh()")

        listOf(
            "renderCoordinator.detach()",
            "searchController.release()",
            "bottomPanelController.release()",
            "operationController.release()",
            "externalActionController.release()",
            "workspaceController.release()"
        ).forEach { assertTrue("Missing view teardown: $it", it in teardown) }

        assertTrue(
            teardown.indexOf("workspaceController.release()") <
                teardown.indexOf("super.onDestroyView()")
        )
    }

    @Test
    fun `controllers retain the extracted integration boundaries`() {
        val operations = source("FileListOperationController.kt")
        val externalActions = source("FileListExternalActionController.kt")
        val workspace = source("FileListWorkspaceController.kt")

        assertTrue("FileOperationService.delete" in operations)
        assertTrue("FileOperationService.encrypt" in operations)
        assertTrue("InternalOpenPolicy.targetAfterExtraction" in externalActions)
        assertTrue("ShortcutManagerCompat.requestPinShortcut" in externalActions)
        assertTrue("coordinator.updateVisibility" in workspace)
        assertTrue("BrowserPaneLayout(context)" in workspace)
    }

    private fun source(name: String): String = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/$name"
    ).readText()

    private companion object {
        const val MAX_FRAGMENT_LINES = 2_350
    }
}
