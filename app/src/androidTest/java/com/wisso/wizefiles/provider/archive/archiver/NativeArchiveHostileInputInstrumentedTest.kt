package com.wisso.wizefiles.provider.archive.archiver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Random
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeArchiveHostileInputInstrumentedTest {
    @Test
    fun deterministicCorruptCorpusDoesNotCrashLibarchiveOrSevenZipBoundaries() {
        val root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "native-archive-fuzz"
        ).apply { deleteRecursively(); mkdirs() }
        val random = Random(0x37415243L)
        try {
            repeat(128) { index ->
                val bytes = ByteArray(random.nextInt(4096) + 1).also(random::nextBytes)
                val libarchiveFailure = runCatching {
                    ReadArchive(ByteArrayInputStream(bytes), emptyList()).use { archive ->
                        while (archive.readEntry(Charsets.UTF_8) != null) Unit
                    }
                }.exceptionOrNull()
                assertTrue(libarchiveFailure == null || libarchiveFailure is IOException)

                if (SevenZipNative.isAvailable()) {
                    val seed = File(root, "seed-$index.bin").apply { writeBytes(bytes) }
                    val sevenZipFailure = runCatching {
                        SevenZipNative.readEntries(seed.toPath(), emptyList())
                    }.exceptionOrNull()
                    assertTrue(sevenZipFailure == null || sevenZipFailure is IOException)
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
