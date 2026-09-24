package com.wisso.wizefiles.feature.filebrowser

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRootPathClassificationTest {

    @Test
    fun `linux file system root is classified as device root`() {
        assertTrue(
            isDeviceRootPathForListing(
                path = Paths.get("/"),
                deviceRootPath = Paths.get("/")
            )
        )
    }

    @Test
    fun `archive file system root is not classified as device root`() {
        val zipPath = createTempFile(prefix = "device-root-check", suffix = ".zip")
        Files.deleteIfExists(zipPath)
        val zipUri = URI.create("jar:${zipPath.toUri()}")

        try {
            FileSystems.newFileSystem(zipUri, mapOf("create" to "true")).use { archiveFs ->
                val archiveRoot = archiveFs.getPath("/")

                assertFalse(
                    isDeviceRootPathForListing(
                        path = archiveRoot,
                        deviceRootPath = Paths.get("/")
                    )
                )
            }
        } finally {
            zipPath.deleteIfExists()
        }
    }
}
