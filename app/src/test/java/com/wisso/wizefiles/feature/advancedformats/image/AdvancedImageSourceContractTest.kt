package com.wisso.wizefiles.feature.advancedformats.image

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedImageSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `advanced formats use bounded preview decoder`() {
        val decoder = source("app/src/main/java/com/wisso/wizefiles/feature/advancedformats/image/AdvancedImagePreviewDecoder.kt")
        val adapter = source("app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewPagerAdapter.kt")
        val policy = source("app/src/main/java/com/wisso/wizefiles/feature/internalviewer/InternalOpenPolicy.kt")
        assertTrue("MAX_SOURCE_BYTES = 256L * 1024 * 1024" in decoder)
        assertTrue("MAX_PIXELS = 8_000_000L" in decoder)
        assertTrue("TgaPreviewDecoder" in decoder)
        assertTrue("IcoPreviewDecoder" in decoder)
        assertTrue("TiffPreviewDecoder" in decoder)
        assertTrue("ExifInterface(input).thumbnailBitmap" in decoder)
        assertTrue("AdvancedImagePreviewDecoder.decode" in adapter)
        assertTrue("FileFormat.Family.IMAGE" in policy)
    }

    private fun source(path: String): String = File(root, path).readText()
}

