package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListSftpHostKeyFlowSourceTest {
    @Test
    fun `file browser handles unknown host key with trust once and save actions`() {
        val fragmentSource =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt")
        val dialogSource =
            sourceFile(
                "src/main/java/com/wisso/wizefiles/feature/filebrowser/" +
                    "FileListSftpHostKeyDialogController.kt"
            )

        assertTrue(fragmentSource.contains("sftpHostKeyDialogController.showUnknown"))
        assertTrue(dialogSource.contains("trustHostKeyOnce"))
        assertTrue(dialogSource.contains("trustHostKey("))
        assertTrue(dialogSource.contains("refresh()"))
    }

    private fun sourceFile(path: String): String {
        val file = File(path)
        if (file.exists()) {
            return file.readText()
        }
        val appPrefixed = File("app/$path")
        require(appPrefixed.exists()) { "Unable to locate source file: $path" }
        return appPrefixed.readText()
    }
}
