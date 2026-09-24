package com.wisso.wizefiles.core.files.provider.legacy

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFileProviderBridgeSourceTest {

    @Test
    fun legacyFileProviderBridgeRoutesEverySchemeThroughInstalledProviders() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/core/files/provider/legacy/LegacyFileProviderBridge.kt")

        assertTrue(source.contains("FileSystemProviders[scheme].getPath(pathUri)"))
        assertFalse(source.contains("FtpFileSystemProvider"))
        assertFalse(source.contains("Paths.get(pathUri)"))
        assertTrue(source.contains(".encodedPath(\"/\$uriPath\")"))
        assertTrue(source.contains("val decodedOnce = Uri.decode(encodedLegacyPath)"))
        assertTrue(source.contains("val decodedPath = if (\"://\" in decodedOnce)"))
        assertTrue(source.contains("Uri.decode(decodedOnce)"))
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) return direct.readText()
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        throw java.io.FileNotFoundException(path)
    }
}
