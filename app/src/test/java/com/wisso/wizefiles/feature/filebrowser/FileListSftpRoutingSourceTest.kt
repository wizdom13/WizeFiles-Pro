package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListSftpRoutingSourceTest {

    @Test
    fun `sftp paths are listed through provider flow not legacy adapter fallback`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListLiveData.kt")

        val sftpBranchIndex = source.indexOf("else if (path.isSftpPath)")
        val legacyFallbackIndex = source.indexOf("legacyRetrofileAdapter.listFileItems")

        assertTrue(source.contains("path.isSftpPath"))
        assertTrue(source.contains("loadArchiveFileList(path)"))
        assertTrue(sftpBranchIndex >= 0)
        assertTrue(legacyFallbackIndex >= 0)
        assertTrue("SFTP routing must be resolved before legacy fallback", sftpBranchIndex < legacyFallbackIndex)
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
