package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCryptoMenuSourceTest {
    @Test
    fun selectionMenuContainsEncryptDecryptActions() {
        val selectMenu = readFirstExisting(
            "src/main/res/menu/menu_file_list_select.xml",
            "app/src/main/res/menu/menu_file_list_select.xml"
        )

        assertTrue(selectMenu.contains("@+id/action_encrypt"))
        assertTrue(selectMenu.contains("@drawable/ic_lock_control_normal_24dp"))
        assertTrue(selectMenu.contains("@+id/action_decrypt"))
        assertTrue(selectMenu.contains("@drawable/ic_unlock_control_normal_24dp"))
        assertFalse(selectMenu.contains("@drawable/ic_lock_white_24dp"))
        assertFalse(
            listOf(
                "src/main/res/menu/menu_file_item.xml",
                "app/src/main/res/menu/menu_file_item.xml"
            ).any { File(it).exists() }
        )
    }

    @Test
    fun fileListFragmentWiresEncryptDecryptDialogs() {
        val source = readFirstExisting(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ) + readFirstExisting(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt"
        ) + readFirstExisting(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListOperationController.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListOperationController.kt"
        )
        assertTrue(source.contains("EncryptFilesDialogFragment"))
        assertTrue(source.contains("DecryptFilesDialogFragment"))
        assertTrue(source.contains("FileOperationService.encrypt"))
        assertTrue(source.contains("FileOperationService.decrypt"))
        assertTrue(source.contains("it.attributes.isDirectory || it.name.endsWith(\".enc\")"))
        assertTrue(source.contains("!isAnyFileReadOnly && areAllFilesDecryptable"))
    }

    @Test
    fun centralizedSelectionMenuGatesDecryptAndWriteVisibility() {
        val source = readFirstExisting(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ) + readFirstExisting(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt"
        )
        assertTrue(source.contains("menu.findItem(R.id.action_encrypt).isVisible = !isAnyFileReadOnly"))
        assertTrue(source.contains("menu.findItem(R.id.action_decrypt).isVisible ="))
        assertTrue(source.contains("!isAnyFileReadOnly && areAllFilesDecryptable"))
    }

    private fun readFirstExisting(vararg paths: String): String {
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: throw FileNotFoundException(paths.joinToString())
        return file.readText()
    }
}
