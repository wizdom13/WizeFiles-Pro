// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProviderRoutingSourceTest {
    @Test
    fun `scanner resolves app paths and walks their registered nio provider`() {
        val scanner = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/sync/SyncTreeScanner.kt"
        )

        assertTrue(scanner.contains("toLegacyPathOrNull()"))
        assertTrue(scanner.contains("Files.walkFileTree(root"))
        assertTrue(scanner.contains("Files.readAttributes").not())
        assertTrue(scanner.contains("storageIdentity(rootUri)"))
        assertFalse(scanner.contains("relativize(path).joinToString"))
    }

    @Test
    fun `relative path serialization does not iterate provider path components`() {
        val root = NonIterablePath(Paths.get("/source"))
        val child = NonIterablePath(Paths.get("/source/trips/report.pdf"))

        assertEquals("trips/report.pdf", syncRelativePath(root, child))
    }

    @Test
    fun `sync mutations stay on the provider facade and never enter legacy fallback`() {
        val executor = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/sync/SyncActionExecutor.kt"
        )
        val facade = projectFile(
            "src/main/java/com/wisso/wizefiles/storage/StorageFacade.kt"
        )

        assertTrue(executor.contains("storageFacade.copy(source, temporary"))
        assertTrue(executor.contains("storageFacade.createDirectories(target)"))
        assertTrue(executor.contains("storageFacade.delete(source)"))
        assertTrue(executor.contains("storageFacade.deleteIfExists(source)"))
        assertTrue(facade.contains("Files.createDirectory(path)"))
        assertTrue(facade.contains("Files.delete(path)"))
        assertFalse(facade.contains("legacy.createDirectory(path.toAppPath())"))
        assertFalse(facade.contains("legacy.delete(path.toAppPath())"))
    }

    @Test
    fun `temporary finalization and recovery retain provider paths`() {
        val executor = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/sync/SyncActionExecutor.kt"
        )

        assertTrue(executor.contains("temporary.toAppPath().toUriString()"))
        assertTrue(executor.contains("recoveredTargetIsValid(target"))
        assertTrue(executor.contains("StandardCopyOption.ATOMIC_MOVE"))
        assertTrue(executor.contains("AtomicMoveNotSupportedException"))
    }

    @Test
    fun `profile editor scrolls and uses material text fields`() {
        val layout = projectFile("src/main/res/layout/dialog_sync_profile.xml")
        val activity = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/sync/SyncProfilesActivity.kt"
        )

        assertTrue(layout.contains("<androidx.core.widget.NestedScrollView"))
        assertTrue(layout.contains("TextInputEditText"))
        assertFalse(layout.contains("<EditText"))
        assertTrue(activity.contains("updateScheduleVisibility()"))
        assertTrue(activity.contains("AppLog.e("))
    }

    private fun projectFile(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull(File::exists)?.readText()
            ?: error("Unable to locate source file: $path")
    }

    private class NonIterablePath(private val delegate: Path) : Path by delegate {
        override fun relativize(other: Path): Path {
            val otherDelegate = (other as NonIterablePath).delegate
            return NonIterablePath(delegate.relativize(otherDelegate))
        }

        override fun iterator(): MutableIterator<Path> =
            throw AssertionError("Provider path components must not be iterated")

        override fun toString(): String = delegate.toString()
    }
}
