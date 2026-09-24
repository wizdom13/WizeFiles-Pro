package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Paths
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wisso.wizefiles.provider.archive.createArchiveRootPath

class FileListFragmentBackNavigationTest {

    @Test
    fun backInsideInternalStorage_navigatesUpToParent() {
        assertTrue(
            BrowserRestorationPolicy.shouldNavigateUp(
                currentPath = Paths.get("/storage/emulated/0/Download/Subfolder"),
                storageRootPath = Paths.get("/storage/emulated/0")
            )
        )
    }

    @Test
    fun backAtTopOfInternalStorage_doesNotNavigateUp() {
        assertFalse(
            BrowserRestorationPolicy.shouldNavigateUp(
                currentPath = Paths.get("/storage/emulated/0"),
                storageRootPath = Paths.get("/storage/emulated/0")
            )
        )
    }

    @Test
    fun backInsideDeviceRoot_navigatesUpToParent() {
        assertTrue(
            BrowserRestorationPolicy.shouldNavigateUp(
                currentPath = Paths.get("/system/bin"),
                storageRootPath = Paths.get("/")
            )
        )
    }

    @Test
    fun backAtDeviceRoot_doesNotNavigateUp() {
        assertFalse(
            BrowserRestorationPolicy.shouldNavigateUp(
                currentPath = Paths.get("/"),
                storageRootPath = Paths.get("/")
            )
        )
    }

    @Test
    fun backAtArchiveRoot_navigatesToContainingFolder() {
        val archivePath = createTempFile(prefix = "back-archive-root", suffix = ".zip")
        try {
            assertTrue(
                BrowserRestorationPolicy.shouldNavigateUp(
                    currentPath = archivePath.createArchiveRootPath(),
                    storageRootPath = Paths.get("/storage/emulated/0")
                )
            )
        } finally {
            archivePath.deleteIfExists()
        }
    }
}
