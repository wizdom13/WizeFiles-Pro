// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import com.wisso.wizefiles.storage.legacy.listLegacyDirectoryEntries
import com.wisso.wizefiles.storage.legacy.readFileItemSnapshot
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempDirectory
import java.util.UUID
import kotlin.io.path.createTempFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPathBridgeRegressionTest {

    @Test
    fun `provider-backed non-local path uses RawAppPath and round-trips`() {
        val zipFile = createTempDirectory("bridge-provider").resolve("provider.zip").toFile()
        zipFile.deleteOnExit()
        val uri = java.net.URI.create("jar:${zipFile.toURI()}")
        FileSystems.newFileSystem(uri, mapOf("create" to "true")).use { fileSystem ->
            val filePath = fileSystem.getPath("/folder/provider.txt")
            Files.createDirectories(filePath.parent)
            Files.write(filePath, "provider-${UUID.randomUUID()}".toByteArray(StandardCharsets.UTF_8))

            val appPath = filePath.toAppPath()
            assertTrue(appPath is RawAppPath)
            assertEquals(filePath.toUri().toString(), appPath.rawPath)

            val roundTrip = appPath.toLegacyPathOrNull()
            assertNotNull(roundTrip)
            assertEquals(filePath.toUri(), roundTrip!!.toUri())

            val attributes = Files.readAttributes(roundTrip, java.nio.file.attribute.BasicFileAttributes::class.java)
            assertFalse(attributes.isDirectory)
        }
    }

    @Test
    fun `legacy file list flow supports provider-backed app path without toFile`() {
        val zipFile = createTempDirectory("bridge-list").resolve("listing.zip").toFile()
        zipFile.deleteOnExit()
        val uri = java.net.URI.create("jar:${zipFile.toURI()}")
        FileSystems.newFileSystem(uri, mapOf("create" to "true")).use { fileSystem ->
            val dirPath = fileSystem.getPath("/folder")
            val childPath = fileSystem.getPath("/folder/listing.txt")
            Files.createDirectories(dirPath)
            Files.write(childPath, "listing".toByteArray(StandardCharsets.UTF_8))

            val items = dirPath.toAppPath().listLegacyDirectoryEntries()

            assertEquals(1, items.size)
            assertEquals("listing.txt", items.single().name)
        }
    }

    @Test
    fun `readFileItemSnapshot supports non-local round-trippable directory path`() {
        val zipFile = createTempDirectory("bridge-snapshot").resolve("snapshot.zip").toFile()
        zipFile.deleteOnExit()
        val uri = java.net.URI.create("jar:${zipFile.toURI()}")
        FileSystems.newFileSystem(uri, mapOf("create" to "true")).use { fileSystem ->
            val dirPath = fileSystem.getPath("/folder")
            Files.createDirectories(dirPath)

            val snapshot = dirPath.toAppPath().readFileItemSnapshot()

            assertTrue(snapshot.metadataNoFollowLinks.isDirectory)
            assertEquals("folder", dirPath.toAppPath().name)
        }
    }

    @Test
    fun `local path still uses LocalAppPath and round-trips`() {
        val localFile = File.createTempFile("bridge-local", ".txt").apply {
            deleteOnExit()
            writeText("local")
        }
        val localPath = localFile.toPath()

        val appPath = localPath.toAppPath()
        if (appPath is LocalAppPath) {
            assertEquals(localPath.toString(), appPath.rawPath)
        } else {
            assertEquals(localPath.toUri().toString(), appPath.rawPath)
        }

        val roundTrip = appPath.toLegacyPathOrNull()
        assertNotNull(roundTrip)
        assertEquals(localPath.toString(), roundTrip.toString())
    }

    @Test
    fun `archive branch remains URI-backed and does not coerce with toFile`() {
        val inboundSource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/path/legacy/LegacyPathInboundInterop.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/path/legacy/LegacyPathInboundInterop.kt"
        )
        val interopSource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/path/legacy/LegacyPathInterop.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/path/legacy/LegacyPathInterop.kt"
        )
        val loaderSource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/legacy/LegacyFileItemLoader.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/legacy/LegacyFileItemLoader.kt"
        )

        assertTrue(inboundSource.contains("isArchivePath -> RawAppPath(toUri().toString())"))
        assertTrue(inboundSource.contains("isLinuxPath || isLocalFileSchemePath() -> LocalAppPath(toFile())"))
        assertTrue(inboundSource.contains("else -> RawAppPath(toUri().toString())"))
        assertTrue(interopSource.contains("ArchiveFileSystemProvider.getPath(uri)"))
        assertTrue(interopSource.contains("DocumentFileSystemProvider.getPath(uri)"))
        assertTrue(interopSource.contains("ContentFileSystemProvider.getPath(uri)"))
        assertTrue(interopSource.contains("\"ftp\", \"ftps\" -> runCatching { FtpFileSystemProvider.getPath(uri) }.getOrNull()"))
        assertTrue(interopSource.contains("\"sftp\" -> runCatching { SftpFileSystemProvider.getPath(uri) }.getOrNull()"))
        assertTrue(loaderSource.contains("toLegacyPathOrNull()"))
        assertTrue(loaderSource.contains("LinkOption.NOFOLLOW_LINKS"))
    }

    @Test
    fun `file uri path uses LocalAppPath for local fallback operations`() {
        val localFile = createTempFile("bridge-file-uri", ".txt")
        Files.write(localFile, "local-uri".toByteArray(StandardCharsets.UTF_8))
        val fileUriPath = Paths.get(localFile.toUri())

        val appPath = fileUriPath.toAppPath()

        assertTrue(appPath is LocalAppPath)
        assertEquals(localFile.toString(), appPath.rawPath)
    }

    @Test
    fun `uri-like non-file raw path does not fallback to local Paths-get string parsing`() {
        val malformedDocumentPath = RawAppPath("document:/content:/authority/tree/Dropbox%253A?")

        val roundTrip = malformedDocumentPath.toLegacyPathOrNull()

        assertEquals(null, roundTrip)
    }

    @Test
    fun `smb-style raw paths resolve through smb provider interop`() {
        val smbPath = RawAppPath("smb://user@192.169.0.64/")
        val smb2Path = RawAppPath("smb2://user@192.169.0.64/share")
        val smb3Path = RawAppPath("smb3://user@192.169.0.64/share/folder")

        val resolvedSmb = smbPath.toLegacyPathOrNull()
        val resolvedSmb2 = smb2Path.toLegacyPathOrNull()
        val resolvedSmb3 = smb3Path.toLegacyPathOrNull()

        assertNotNull(resolvedSmb)
        assertNotNull(resolvedSmb2)
        assertNotNull(resolvedSmb3)
        assertEquals("smb", resolvedSmb!!.fileSystem.provider().scheme)
        assertEquals("smb", resolvedSmb2!!.fileSystem.provider().scheme)
        assertEquals("smb", resolvedSmb3!!.fileSystem.provider().scheme)
    }

    @Test
    fun `sftp raw path resolves through sftp provider interop`() {
        val sftpPath = RawAppPath("sftp://demo@example.com:22/")

        val resolved = sftpPath.toLegacyPathOrNull()

        assertNotNull(resolved)
        assertEquals("sftp", resolved!!.fileSystem.provider().scheme)
    }

    private fun readProjectFile(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Unable to locate any of: ${candidates.joinToString()}")
        return file.readText()
    }
}
