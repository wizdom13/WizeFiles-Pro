package com.wisso.wizefiles.storage

import com.wisso.wizefiles.provider.common.ProgressCopyOption
import com.wisso.wizefiles.provider.common.toCopyOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.CopyOption
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCopyMoveOptionPlumbingTest {

    @Test
    fun `copy option parser preserves replace copy-attrs nofollow and progress`() {
        var progressCalled = false
        val options = arrayOf<CopyOption>(
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES,
            LinkOption.NOFOLLOW_LINKS,
            ProgressCopyOption(1L) { progressCalled = true }
        )

        val parsed = options.toCopyOptions()
        assertTrue(parsed.replaceExisting)
        assertTrue(parsed.copyAttributes)
        assertTrue(parsed.noFollowLinks)
        assertFalse(parsed.atomicMove)

        val rebuilt = parsed.toArray()
        assertTrue(rebuilt.any { it == StandardCopyOption.REPLACE_EXISTING })
        assertTrue(rebuilt.any { it == StandardCopyOption.COPY_ATTRIBUTES })
        assertTrue(rebuilt.any { it == LinkOption.NOFOLLOW_LINKS })

        val rebuiltProgress = rebuilt.filterIsInstance<ProgressCopyOption>().firstOrNull()
        assertNotNull(rebuiltProgress)
        rebuiltProgress!!.listener(7L)
        assertTrue(progressCalled)
    }

    @Test
    fun `storage facade and router forward vararg options for copy and move`() {
        val facadeSource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/StorageFacade.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/StorageFacade.kt"
        )
        assertTrue(facadeSource.contains("router.copy(source, target, *options)"))
        assertTrue(facadeSource.contains("router.move(source, target, *options)"))
        assertTrue(facadeSource.contains("legacy.copy(source.toAppPath(), target.toAppPath(), *options)"))
        assertTrue(facadeSource.contains("legacy.move(source.toAppPath(), target.toAppPath(), *options)"))

        val routerSource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/StorageRouter.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/StorageRouter.kt"
        )
        assertTrue(routerSource.contains("fun copy(source: Path, target: Path, vararg options: CopyOption): Boolean"))
        assertTrue(routerSource.contains("fun move(source: Path, target: Path, vararg options: CopyOption): Boolean"))
        assertTrue(routerSource.contains("localStorageBackend.copy(LocalFileNode(sourceFile), LocalFileNode(targetFile), *options)"))
        assertTrue(routerSource.contains("localStorageBackend.move(LocalFileNode(sourceFile), LocalFileNode(targetFile), *options)"))
    }

    @Test
    fun `local local copy honors replace existing through facade`() {
        val source = Files.createTempFile("copy-options-source", ".txt")
        val target = Files.createTempFile("copy-options-target", ".txt")
        Files.write(source, "source".toByteArray())
        Files.write(target, "target".toByteArray())

        StorageFacade().copy(source, target, StandardCopyOption.REPLACE_EXISTING)

        assertEquals("source", String(Files.readAllBytes(target)))
    }

    @Test
    fun `local local copy honors copy attributes through facade`() {
        val source = Files.createTempFile("copy-attrs-source", ".txt")
        val target = Files.createTempFile("copy-attrs-target", ".txt")
        val expectedTime = FileTime.from(1234, TimeUnit.SECONDS)
        Files.setLastModifiedTime(source, expectedTime)

        StorageFacade().copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)

        assertEquals(expectedTime.toMillis(), Files.getLastModifiedTime(target).toMillis())
    }

    @Test
    fun `local local copy honors nofollow links through facade`() {
        val directory = Files.createTempDirectory("copy-nofollow")
        val targetFile = directory.resolve("target.txt")
        val sourceLink = directory.resolve("source-link.txt")
        val copiedLink = directory.resolve("copied-link.txt")
        Files.write(targetFile, "target".toByteArray())
        Files.deleteIfExists(sourceLink)
        Files.deleteIfExists(copiedLink)
        Files.createSymbolicLink(sourceLink, targetFile.fileName)

        StorageFacade().copy(sourceLink, copiedLink, LinkOption.NOFOLLOW_LINKS)

        assertTrue(Files.isSymbolicLink(copiedLink))
    }

    @Test
    fun `legacy fallback copy handles progress option without unsupported copy option crash`() {
        val source = Files.createTempFile("copy-progress-source", ".txt")
        val target = Files.createTempFile("copy-progress-target", ".txt")
        Files.write(source, "source".toByteArray())
        Files.write(target, "target".toByteArray())
        var reportedProgress = 0L

        StorageFacade().copy(
            source,
            target,
            StandardCopyOption.REPLACE_EXISTING,
            ProgressCopyOption(1L) { reportedProgress += it }
        )

        assertEquals("source", String(Files.readAllBytes(target)))
        assertTrue(reportedProgress > 0L)
    }


    @Test
    fun `linux path storage access logic does not depend on global application singleton`() {
        val linuxPathSource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/data/providers/os/LinuxPath.kt",
            "app/src/main/java/com/wisso/wizefiles/data/providers/os/LinuxPath.kt"
        )
        assertTrue(linuxPathSource.contains("BuildConfig.APPLICATION_ID"))
        assertFalse(linuxPathSource.contains("application.packageName"))
    }

    @Test
    fun `local and legacy implementations do not drop copy options`() {
        val backendSource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/local/LocalStorageBackend.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/local/LocalStorageBackend.kt"
        )
        assertTrue(backendSource.contains("copy(source: LocalFileNode, target: LocalFileNode, vararg options: CopyOption)"))
        assertTrue(backendSource.contains("move(source: LocalFileNode, target: LocalFileNode, vararg options: CopyOption)"))
        assertTrue(backendSource.contains("copyTo(target.file.toPath(), *options)"))
        assertTrue(backendSource.contains("moveTo(target.file.toPath(), *options)"))

        val legacySource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/storage/legacy/LegacyRetrofilePathCompat.kt",
            "app/src/main/java/com/wisso/wizefiles/storage/legacy/LegacyRetrofilePathCompat.kt"
        )
        assertFalse(legacySource.contains("Files.copy(sourceFile.toPath(), targetFile.toPath())"))
        assertFalse(legacySource.contains("Files.move(sourceFile.toPath(), targetFile.toPath())"))
        assertTrue(legacySource.contains("copyLegacyPathTo(target: AppPath, vararg options: CopyOption)"))
        assertTrue(legacySource.contains("moveLegacyPathTo(target: AppPath, vararg options: CopyOption)"))
        assertTrue(legacySource.contains("sourcePath.copyTo(targetPath, *options)"))
        assertTrue(legacySource.contains("sourcePath.moveTo(targetPath, *options)"))
    }

    private fun readProjectFile(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Unable to locate any of: ${candidates.joinToString()}")
        return file.readText()
    }
}
