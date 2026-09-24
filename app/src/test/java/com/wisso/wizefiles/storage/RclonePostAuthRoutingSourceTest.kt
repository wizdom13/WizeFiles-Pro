// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RclonePostAuthRoutingSourceTest {

    @Test
    fun `app paths resolve rclone directories through the nio provider`() {
        val source = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/path/legacy/LegacyPathInterop.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/path/legacy/LegacyPathInterop.kt"
        )

        assertTrue(source.contains("import com.wisso.wizefiles.provider.rclone.RcloneFileSystemProvider"))
        assertTrue(
            source.contains(
                "\"rclone\" -> runCatching { RcloneFileSystemProvider.getPath(uri) }.getOrNull()"
            )
        )
    }

    @Test
    fun `nio-only transfers bypass the legacy adapter`() {
        val source = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/StorageFacade.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/StorageFacade.kt"
        )

        val rcloneBranch = source.indexOf("source.isRclonePath || target.isRclonePath")
        val sameProviderBranch = source.indexOf(
            "source.fileSystem.provider() == target.fileSystem.provider()"
        )
        val foreignCopy = source.indexOf("ForeignCopyMove.copy(source, target, *options)")
        val foreignMove = source.indexOf("ForeignCopyMove.move(source, target, *options)")

        assertTrue(source.contains("import com.wisso.wizefiles.provider.rclone.isRclonePath"))
        assertTrue(rcloneBranch >= 0)
        assertTrue(sameProviderBranch > rcloneBranch)
        assertTrue(foreignCopy > sameProviderBranch)
        assertTrue(foreignMove > sameProviderBranch)
        assertTrue(source.contains("source.copyTo(target, *options)"))
        assertTrue(source.contains("source.moveTo(target, *options)"))
    }

    @Test
    fun `remote create and delete use the native provider instead of legacy local fallback`() {
        val source = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/StorageFacade.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/StorageFacade.kt"
        )

        assertTrue(source.contains("Files.createDirectory(path)"))
        assertTrue(source.contains("Files.createDirectories(path)"))
        assertTrue(source.contains("Files.delete(path)"))
        assertTrue(source.contains("Files.deleteIfExists(path)"))
        assertTrue(!source.contains("legacy.createDirectory(path.toAppPath())"))
        assertTrue(!source.contains("legacy.delete(path.toAppPath())"))
    }

    @Test
    fun `cloud to local copy accepts no follow links for rclone reads`() {
        val provider = readProjectFile(
            "src/main/java/com/wisso/wizefiles/data/providers/rclone/RcloneFileSystemProvider.kt",
            "app/src/main/java/com/wisso/wizefiles/data/providers/rclone/RcloneFileSystemProvider.kt"
        )

        assertTrue(
            provider.contains(
                "it != StandardOpenOption.READ && it != LinkOption.NOFOLLOW_LINKS"
            )
        )
    }

    @Test
    fun `local to cloud copy skips unsupported timestamp preservation`() {
        val foreignCopyMove = readProjectFile(
            "src/main/java/com/wisso/wizefiles/data/providers/common/ForeignCopyMove.kt",
            "app/src/main/java/com/wisso/wizefiles/data/providers/common/ForeignCopyMove.kt"
        )

        assertTrue(
            foreignCopyMove.contains(
                "getFileAttributeView(BasicFileAttributeView::class.java) ?: return"
            )
        )
        assertTrue(
            !foreignCopyMove.contains(
                "getFileAttributeView(BasicFileAttributeView::class.java)!!"
            )
        )
    }

    private fun readProjectFile(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Unable to locate any of: ${candidates.joinToString()}")
        return file.readText()
    }
}
