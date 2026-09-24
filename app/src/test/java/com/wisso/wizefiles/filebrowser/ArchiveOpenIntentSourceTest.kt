package com.wisso.wizefiles.filebrowser

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filebrowser.BrowserNavigationFactsResolver
import com.wisso.wizefiles.feature.filebrowser.BrowserRestorationPolicy
import com.wisso.wizefiles.feature.filebrowser.FileListFragment
import com.wisso.wizefiles.provider.archive.ArchiveFileSystemProvider
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.archive.archiver.ArchiveReader
import com.wisso.wizefiles.provider.content.ContentFileSystemProvider
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArchiveOpenIntentSourceTest {

    @Before
    fun setUp() {
        setGlobalApplicationForTests(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `archive view startup tolerates menu preparation before path initialization`() {
        assertFalse(FileListFragment.isInRecycleBinContext(null))
    }

    @Test
    fun `content uri archive root survives app path round trip`() {
        val sourceUri = "content://media/external/downloads/44225"
        val sourcePath = ContentFileSystemProvider.getPath(URI.create(sourceUri))
        val serializedArchiveUri = sourcePath.createArchiveRootPath().toUri()

        val resolvedPath = ArchiveFileSystemProvider.getPath(serializedArchiveUri)

        assertTrue(resolvedPath.isArchivePath)
        assertEquals(sourceUri, resolvedPath.archiveFile.rawPath)
    }

    @Test
    fun `opaque generic content uri is treated as an archive view`() {
        val sourcePath = ContentFileSystemProvider.getPath(
            URI.create("content://provider/files/44225")
        )

        assertEquals("44225", sourcePath.fileName.toString())
        assertTrue(
            FileListFragment.shouldOpenAsArchiveView(
                Intent.ACTION_VIEW,
                sourcePath,
                MimeType.GENERIC
            )
        )
        assertTrue(
            FileListFragment.shouldOpenAsArchiveView(
                Intent.ACTION_VIEW,
                sourcePath,
                null
            )
        )
        assertFalse(
            FileListFragment.shouldOpenAsArchiveView(
                Intent.ACTION_SEND,
                sourcePath,
                MimeType.GENERIC
            )
        )
    }

    @Test
    fun `external archive root defers Back to browser return location`() {
        val sourcePath = ContentFileSystemProvider.getPath(
            URI.create("content://provider/files/test.zip")
        )
        val archiveRoot = sourcePath.createArchiveRootPath()
        try {
            assertTrue(BrowserNavigationFactsResolver.isExternalArchiveRoot(archiveRoot))
            assertFalse(
                BrowserRestorationPolicy.shouldNavigateUp(
                    currentPath = archiveRoot,
                    storageRootPath = null
                )
            )
        } finally {
            archiveRoot.fileSystem.close()
        }
    }

    @Test
    fun `non seekable content descriptor is rejected before libarchive`() {
        assertFalse(ArchiveReader.isUsableSeekableChannel(NonSeekableChannel()))
    }

    @Test
    fun `content archive is staged before the initial metadata scan`() {
        val source = projectFile(
            "src/main/java/com/wisso/wizefiles/data/providers/archive/ArchiveFileSystem.kt"
        )
        val method = source.substringAfter("private fun readEntriesWithContentFallbackLocked()")
            .substringBefore("private fun readerArchiveFileLocked()")
        val contentCheck = method.indexOf("if (sourceFile.isContentPath)")
        val staging = method.indexOf("stageArchiveFileLocked(sourceFile)")
        val directRead = method.indexOf("ArchiveReader.readEntries(sourceFile")

        assertTrue(contentCheck >= 0)
        assertTrue(staging > contentCheck)
        assertTrue(directRead > staging)
    }

    @Test
    fun `named external handlers advertise supported file and media intents`() {
        val manifest = projectFile("src/main/AndroidManifest.xml")
        val aliasesStart = manifest.indexOf(
            "android:name=\"com.wisso.wizefiles.feature.filebrowser.ExternalAudioPlayer\""
        )
        val aliasesEnd = manifest.indexOf(
            "android:name=\"com.wisso.wizefiles.feature.mediapreview.MediaPreviewActivity\"",
            aliasesStart
        )
        assertTrue(aliasesStart >= 0)
        assertTrue(aliasesEnd > aliasesStart)
        val externalHandlers = manifest.substring(aliasesStart, aliasesEnd)

        assertTrue(externalHandlers.contains("android.intent.action.VIEW"))
        assertTrue(externalHandlers.contains("android.intent.category.DEFAULT"))
        assertTrue(externalHandlers.contains("android:mimeType=\"application/zip\""))
        assertTrue(externalHandlers.contains("android:mimeType=\"application/x-7z-compressed\""))
        assertTrue(externalHandlers.contains("android:mimeType=\"application/vnd.rar\""))
        assertTrue(externalHandlers.contains("android:mimeType=\"application/x-rar-compressed\""))
        assertTrue(externalHandlers.contains("android:mimeType=\"audio/*\""))
        assertTrue(externalHandlers.contains("android:mimeType=\"video/*\""))
        assertTrue(externalHandlers.contains("android:pathPattern=\".*\\.wzf\""))
        assertFalse(externalHandlers.contains("android.intent.category.BROWSABLE"))
    }

    @Test
    fun `generic archive intent filter accepts opaque content uris`() {
        val manifest = projectFile("src/main/AndroidManifest.xml")
        val fallbackStart = manifest.indexOf(
            "Some document providers expose supported files as application/octet-stream."
        )
        val fallbackEnd = manifest.indexOf("</intent-filter>", fallbackStart)
        assertTrue(fallbackStart >= 0)
        assertTrue(fallbackEnd > fallbackStart)
        val fallback = manifest.substring(fallbackStart, fallbackEnd)

        assertTrue(fallback.contains("android:mimeType=\"application/octet-stream\""))
        assertTrue(fallback.contains("android:scheme=\"content\""))
        assertTrue(fallback.contains("android:host=\"*\""))
        assertFalse(fallback.contains("android:scheme=\"file\""))
        assertFalse(fallback.contains("android:pathPattern"))
        assertFalse(fallback.contains("android:mimeType=\"*/*\""))
    }

    private class NonSeekableChannel : SeekableByteChannel {
        private var open = true

        override fun read(destination: ByteBuffer): Int = -1

        override fun write(source: ByteBuffer): Int =
            throw UnsupportedOperationException()

        override fun position(): Long = throw IOException("Illegal seek")

        override fun position(newPosition: Long): SeekableByteChannel =
            throw IOException("Illegal seek")

        override fun size(): Long = 0

        override fun truncate(size: Long): SeekableByteChannel =
            throw UnsupportedOperationException()

        override fun isOpen(): Boolean = open

        override fun close() {
            open = false
        }
    }

    private fun projectFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) return direct.readText()
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        throw java.io.FileNotFoundException(path)
    }
}
