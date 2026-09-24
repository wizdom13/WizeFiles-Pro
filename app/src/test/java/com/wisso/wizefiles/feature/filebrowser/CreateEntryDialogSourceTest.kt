package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateEntryDialogSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `browser create dialogs match the Vault dialog presentation`() {
        val nameDialog = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/NameDialogFragment.kt"
        )
        val fileNameDialog = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileNameDialogFragment.kt"
        )
        val fileList = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val externalActions = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListExternalActionController.kt"
        )
        val strings = sourceFile("app/src/main/res/values/strings.xml")
        val createFileDialog = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/CreateFileDialogFragment.kt"
        )
        val createDirectoryDialog = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/CreateDirectoryDialogFragment.kt"
        )
        val createEntryLayout = sourceFile(
            "app/src/main/res/layout/dialog_create_entry_name.xml"
        )

        assertTrue("MaterialAlertDialogBuilder(requireContext(), theme)" in nameDialog)
        assertTrue("R.layout.dialog_create_entry_name" in fileNameDialog)
        assertTrue("useCreateEntryDialogStyle: Boolean = true" in createFileDialog)
        assertTrue("useCreateEntryDialogStyle: Boolean = true" in createDirectoryDialog)
        assertTrue("R.string.file_list_action_create_file" in createFileDialog)
        assertTrue("R.string.file_list_action_create_directory" in createDirectoryDialog)
        assertTrue("android:paddingStart=\"@dimen/dialog_padding\"" in createEntryLayout)
        assertTrue("android:paddingEnd=\"@dimen/dialog_padding\"" in createEntryLayout)
        assertTrue("<EditText" in createEntryLayout)
        assertFalse("TextInputLayout" in createEntryLayout)
        assertTrue("bindingRoot.findViewById<EditText>(R.id.nameEdit)" in nameDialog)
        assertTrue("binding.showNameError" in fileNameDialog)
        assertTrue("listener.hasEntryWithName(name)" in fileNameDialog)
        assertTrue("fun hasEntryWithName(name: String): Boolean" in fileNameDialog)
        assertFalse("hasFileWithName" in fileNameDialog)
        assertTrue("override fun hasEntryWithName(name: String)" in fileList)
        assertTrue("externalActionController.entryWithName(name)" in fileList)
        assertTrue("fun entryWithName(name: String): FileItem?" in externalActions)
        assertFalse("getFileWithName" in fileList)
        assertTrue("An item with this name already exists here" in strings)
        assertFalse("A file with this name already exists here" in strings)
        assertFalse("file_create_file_title" in createFileDialog)
        assertFalse("file_create_directory_title" in createDirectoryDialog)
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
}

