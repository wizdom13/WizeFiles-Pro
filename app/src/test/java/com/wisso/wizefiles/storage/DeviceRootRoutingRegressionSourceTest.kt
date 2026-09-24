package com.wisso.wizefiles.storage

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRootRoutingRegressionSourceTest {

    @Test
    fun `device root uses linux provider path instead of plain Paths get`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/storage/DeviceStorage.kt")

        assertTrue(source.contains("LinuxFileSystemProvider.fileSystem.getPath(linuxPath)"))
        assertTrue(source.contains("get() = storageVolume.pathCompat"))
    }

    @Test
    fun `device root listing has explicit root availability guard`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListLiveData.kt")

        assertTrue(source.contains("if (path.isDeviceRootPath())"))
        assertTrue(source.contains("resolveDeviceRootPathForListing("))
        assertTrue(source.contains("isSuAvailable = LibSuFileServiceLauncher.isSuAvailable()"))
        assertTrue(source.contains("rootPath.toAppPath()"))
    }

    @Test
    fun `legacy directory listing guards root required and returns controlled message`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/storage/legacy/LegacyRetrofilePathCompat.kt")

        assertTrue(source.contains("!local.requiresRootForDirectoryList()"))
        assertTrue(source.contains("ROOT_ACCESS_REQUIRED_MESSAGE"))
        assertTrue(source.contains("catch (e: AccessDeniedException)"))
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) {
            return direct.readText()
        }
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) {
            return fromRepoRoot.readText()
        }
        throw java.io.FileNotFoundException(path)
    }
}
