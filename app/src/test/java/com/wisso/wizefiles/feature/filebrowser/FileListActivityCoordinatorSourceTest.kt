package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListActivityCoordinatorSourceTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `activity delegates system routing and window policy`() {
        val activity = File(
            root,
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivity.kt"
        ).readText()
        listOf(
            "BrowserIntentRouter",
            "BrowserAccessCoordinator",
            "BrowserActivityResultDispatcher",
            "BrowserDrawerCoordinator",
            "BrowserWindowStateController"
        ).forEach { assertTrue("Missing $it delegation", it in activity) }
    }
}
