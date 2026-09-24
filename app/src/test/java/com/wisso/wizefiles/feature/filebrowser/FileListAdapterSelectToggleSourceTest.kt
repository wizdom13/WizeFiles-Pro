package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListAdapterSelectToggleSourceTest {
    @Test
    fun selectionTogglesDirectlyWithoutPerItemMenu() {
        val source = readFirstExisting(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt"
        )

        assertTrue(source.contains("if (file !in selectedFiles)"))
        assertTrue(source.contains("listener.clearSelectedFiles()"))
        assertTrue(source.contains("selectFile(file, true)"))
        assertTrue(source.contains("return hasSelectedFiles || pickOptions?.allowMultiple == true"))
        assertTrue(source.contains("selectFile(file)"))
        assertFalse(source.contains("R.id.action_select"))
        assertFalse(source.contains("holder.popupMenu"))
    }

    private fun readFirstExisting(vararg paths: String): String {
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: throw FileNotFoundException(paths.joinToString())
        return file.readText()
    }
}
