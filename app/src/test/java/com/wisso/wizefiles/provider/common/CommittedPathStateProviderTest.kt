package com.wisso.wizefiles.provider.common

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.LinkOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommittedPathStateProviderTest {
    @Test
    fun `ordinary providers fall back to filesystem existence with nofollow links`() {
        val path = Files.createTempFile("wizefiles-committed-state", ".tmp")
        try {
            assertTrue(path.existsInCommittedStorage(LinkOption.NOFOLLOW_LINKS))

            Files.delete(path)

            assertFalse(path.existsInCommittedStorage(LinkOption.NOFOLLOW_LINKS))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `cloud copy conflict checks use committed state instead of display overlays`() {
        val provider = sourceFile(
            "data/providers/rclone/RcloneFileSystemProvider.kt"
        )
        val transferEngine = sourceFile(
            "feature/filejobs/FileOperationTransferEngine.kt"
        )
        val foreignCopyMove = sourceFile(
            "data/providers/common/ForeignCopyMove.kt"
        )

        assertTrue(provider.contains("CommittedPathStateProvider"))
        assertTrue(provider.contains("override fun existsInCommittedStorage("))
        assertTrue(provider.contains("RcloneEngine::stat"))
        assertTrue(
            transferEngine.countOccurrences("resolvedTarget.existsInCommittedStorage(") >= 3
        )
        assertFalse(transferEngine.contains("resolvedTarget.exists("))
        assertTrue(
            foreignCopyMove.contains(
                "target.existsInCommittedStorage(LinkOption.NOFOLLOW_LINKS)"
            )
        )
        assertTrue(
            foreignCopyMove.contains(
                "path.existsInCommittedStorage(LinkOption.NOFOLLOW_LINKS)"
            )
        )
    }

    private fun sourceFile(relativePath: String): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/$relativePath"),
            File("app/src/main/java/com/wisso/wizefiles/$relativePath")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException(relativePath)
        return file.readText()
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }
}
