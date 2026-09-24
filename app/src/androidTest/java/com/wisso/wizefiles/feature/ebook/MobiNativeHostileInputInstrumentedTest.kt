package com.wisso.wizefiles.feature.ebook

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Random
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobiNativeHostileInputInstrumentedTest {
    @Test
    fun deterministicCorruptCorpusFailsWithoutNativeCrashOrOutputEscape() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nativeLibrary = File(context.applicationInfo.nativeLibraryDir, "libmobi-jni.so")
        assumeTrue(
            "MOBI native library is not packaged for this test ABI",
            nativeLibrary.isFile
        )

        val root = File(context.cacheDir, "mobi-native-fuzz").apply {
            deleteRecursively()
            mkdirs()
        }
        val random = Random(0x4D4F4249L)
        try {
            repeat(128) { index ->
                val source = File(root, "seed-$index.mobi")
                source.writeBytes(ByteArray(random.nextInt(4096) + 1).also(random::nextBytes))
                val output = File(root, "out-$index").apply { mkdir() }
                val failure = runCatching {
                    MobiConverterNative.convertToBundle(source, output)
                }.exceptionOrNull()
                assertTrue(
                    "Unexpected MOBI failure: ${failure?.javaClass?.name}: ${failure?.message}",
                    failure == null || failure is MobiConversionException
                )
                assertTrue(output.canonicalPath.startsWith(root.canonicalPath + File.separator))
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
