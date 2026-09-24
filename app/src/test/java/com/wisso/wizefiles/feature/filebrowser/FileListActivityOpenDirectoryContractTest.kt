package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.storage.path.RawAppPath
import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListActivityOpenDirectoryContractTest {

    @Test
    fun normalizeOpenDirectoryInput_keepsAppPathInputs() {
        val input = RawAppPath("content://docs/tree/root")

        val normalized = FileListActivity.normalizeOpenDirectoryInput(input)

        assertEquals(input, normalized)
    }

    @Test
    fun normalizeOpenDirectoryInput_usesRequestInitialPath() {
        val input = RawAppPath("content://docs/tree/source")
        val request = FileListActivity.OpenDirectoryRequest(
            initialPath = input,
            title = "Choose folder — Source",
            confirmationLabel = "Use this folder as source"
        )

        val normalized = FileListActivity.normalizeOpenDirectoryInput(request)

        assertEquals(input, normalized)
    }

    @Test
    fun resolveOpenDirectoryTitle_usesNonBlankCustomTitleOrFallback() {
        assertEquals(
            "Choose folder — Source",
            FileListActivity.resolveOpenDirectoryTitle(
                "Choose folder — Source",
                "Choose folder"
            )
        )
        assertEquals(
            "Choose folder",
            FileListActivity.resolveOpenDirectoryTitle("  ", "Choose folder")
        )
        assertEquals(
            "Choose folder",
            FileListActivity.resolveOpenDirectoryTitle(null, "Choose folder")
        )
    }

    @Test
    fun resolveOpenDirectoryConfirmationLabel_usesNonBlankCustomLabelOrFallback() {
        assertEquals(
            "Use this folder as source",
            FileListActivity.resolveOpenDirectoryConfirmationLabel(
                "Use this folder as source",
                "Use this folder"
            )
        )
        assertEquals(
            "Use this folder",
            FileListActivity.resolveOpenDirectoryConfirmationLabel("  ", "Use this folder")
        )
        assertEquals(
            "Use this folder",
            FileListActivity.resolveOpenDirectoryConfirmationLabel(null, "Use this folder")
        )
    }

    @Test
    fun normalizeOpenDirectoryInput_convertsLegacyPathInputs() {
        val input = Paths.get("/storage/emulated/0")

        val normalized = FileListActivity.normalizeOpenDirectoryInput(input)

        assertEquals(LocalAppPath(File("/storage/emulated/0")), normalized)
    }

    @Test
    fun normalizeOpenDirectoryInput_rejectsUnsupportedTypes() {
        val normalized = FileListActivity.normalizeOpenDirectoryInput(Any())

        assertNull(normalized)
    }

    @Test
    fun resolveOpenDirectoryPickerResult_returnsTheCurrentDirectory() {
        val currentDirectory = LocalAppPath(File("/storage/emulated/0/Documents"))

        val result = FileListPickerCoordinator.resolveOpenDirectoryResult(
            PickOptions(PickOptions.Mode.OPEN_DIRECTORY, null, false, emptyList(), false, false),
            currentDirectory
        )

        assertEquals(currentDirectory, result)
    }

    @Test
    fun pickerDataUri_isSkippedForDirectoryResults() {
        assertFalse(FileListPickerCoordinator.shouldAttachDataUri(PickOptions.Mode.OPEN_DIRECTORY))
        assertTrue(FileListPickerCoordinator.shouldAttachDataUri(PickOptions.Mode.OPEN_FILE))
        assertTrue(FileListPickerCoordinator.shouldAttachDataUri(PickOptions.Mode.CREATE_FILE))
    }

    @Test
    fun resolveOpenDirectoryPickerResult_rejectsMissingPathAndOtherPickerModes() {
        val currentDirectory = LocalAppPath(File("/storage/emulated/0/Documents"))

        assertNull(
            FileListPickerCoordinator.resolveOpenDirectoryResult(
                PickOptions(PickOptions.Mode.OPEN_DIRECTORY, null, false, emptyList(), false, false),
                null
            )
        )
        assertNull(
            FileListPickerCoordinator.resolveOpenDirectoryResult(
                PickOptions(PickOptions.Mode.OPEN_FILE, null, false, emptyList(), false, false),
                currentDirectory
            )
        )
    }
}
