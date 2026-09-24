package com.wisso.wizefiles.feature.advancedformats

import com.wisso.wizefiles.feature.advancedformats.staging.FormatBundleSource
import com.wisso.wizefiles.feature.advancedformats.staging.FormatStagingLimitException
import com.wisso.wizefiles.feature.advancedformats.staging.FormatStagingStore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InterruptedIOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatStagingStoreTest {
    @Test
    fun `single file completes atomically and session cleanup removes it`() {
        val root = Files.createTempDirectory("format-stage-test").toFile()
        val store = FormatStagingStore(root, "single")
        val bytes = "document".toByteArray()

        val staged = store.stageFile("book.epub", ByteArrayInputStream(bytes), bytes.size.toLong())

        assertTrue(staged.isFile)
        assertArrayEquals(bytes, staged.readBytes())
        assertFalse(store.sessionDirectory.listFiles().orEmpty().any { it.name.endsWith(".partial") })
        store.close()
        assertFalse(store.sessionDirectory.exists())
        root.deleteRecursively()
    }

    @Test
    fun `size limit and cancellation leave no partial files`() {
        val root = Files.createTempDirectory("format-stage-limit").toFile()
        val store = FormatStagingStore(root, "limits")

        assertThrows(FormatStagingLimitException::class.java) {
            store.stageFile(
                "large.bin",
                ByteArrayInputStream(ByteArray(20)),
                expectedSizeBytes = null,
                maxBytes = 10
            )
        }
        assertThrows(InterruptedIOException::class.java) {
            store.stageFile(
                "cancel.bin",
                ByteArrayInputStream(byteArrayOf(1)),
                expectedSizeBytes = null,
                isCancelled = { true }
            )
        }
        assertTrue(store.sessionDirectory.listFiles().orEmpty().isEmpty())
        store.close()
        root.deleteRecursively()
    }

    @Test
    fun `bundle paths cannot escape their staging root`() {
        val root = Files.createTempDirectory("format-stage-bundle").toFile()
        val store = FormatStagingStore(root, "bundle")
        val outside = File(root, "outside.txt")

        assertThrows(IllegalArgumentException::class.java) {
            store.stageBundle(
                listOf(FormatBundleSource("../outside.txt", 1) { ByteArrayInputStream(byteArrayOf(1)) })
            )
        }
        assertFalse(outside.exists())
        assertTrue(store.sessionDirectory.listFiles().orEmpty().isEmpty())
        store.close()
        root.deleteRecursively()
    }

    @Test
    fun `bundle preserves controlled relative resources`() {
        val root = Files.createTempDirectory("format-stage-resources").toFile()
        val store = FormatStagingStore(root, "resources")
        val bundle = store.stageBundle(
            listOf(
                FormatBundleSource("index.html", 5) { ByteArrayInputStream("index".toByteArray()) },
                FormatBundleSource("images/cover.jpg", 5) { ByteArrayInputStream("cover".toByteArray()) }
            )
        )

        assertTrue(File(bundle, "index.html").isFile)
        assertTrue(File(bundle, "images/cover.jpg").isFile)
        store.close()
        root.deleteRecursively()
    }
}
