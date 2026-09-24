// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListRcloneRoutingSourceTest {

    @Test
    fun `rclone paths are listed through provider flow not legacy adapter fallback`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListLiveData.kt")

        val rcloneBranchIndex = source.indexOf("else if (path.isRclonePath)")
        val legacyFallbackIndex = source.indexOf(
            "Success(legacyRetrofileAdapter.listFileItems(path.toAppPath()))"
        )

        assertTrue(source.contains("import com.wisso.wizefiles.provider.rclone.isRclonePath"))
        assertTrue(source.contains("else if (path.isRclonePath)"))
        assertTrue(source.contains("loadRcloneFileList(path)"))
        assertTrue(source.contains("RcloneFileSystemProvider.listWithAttributes("))
        assertTrue(source.contains("applyPendingMutations = true"))
        assertTrue(rcloneBranchIndex >= 0)
        assertTrue(legacyFallbackIndex >= 0)
        assertTrue(
            "rclone routing must be resolved before legacy fallback",
            rcloneBranchIndex < legacyFallbackIndex
        )
    }

    @Test
    fun `rclone listing reuses list metadata and honors cancellation`() {
        val fileList = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListLiveData.kt"
        )
        val provider = sourceFile(
            "src/main/java/com/wisso/wizefiles/data/providers/rclone/RcloneFileSystemProvider.kt"
        )

        val rcloneListing = fileList.substring(
            fileList.indexOf("private fun loadRcloneFileList"),
            fileList.indexOf("private fun Path.toFileItem")
        )

        assertTrue(provider.contains("internal fun listWithAttributes("))
        assertTrue(provider.contains("RcloneEngine.list(path.remoteName, path.remotePath)"))
        assertTrue(rcloneListing.contains("entry.attributes"))
        assertTrue(!rcloneListing.contains("Files.readAttributes"))
        assertTrue(fileList.contains("if (Thread.currentThread().isInterrupted)"))
        assertTrue(fileList.contains("future?.cancel(true)"))
        assertTrue(provider.contains("ensureRequestIsActive()"))
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
