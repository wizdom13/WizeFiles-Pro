package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.core.files.mime.asMimeType
import com.wisso.wizefiles.core.files.mime.isSupportedArchive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathExtensionsArchiveDetectionTest {
    @Test
    fun `archive file name detection matches supported and multipart names`() {
        assertTrue("sample.7z".isSupportedArchiveFileName())
        assertTrue("sample.zip".isSupportedArchiveFileName())
        assertTrue("sample.tar.gz".isSupportedArchiveFileName())
        assertTrue("sample.TGZ".isSupportedArchiveFileName())
        assertTrue("sample.iso".isSupportedArchiveFileName())
        assertTrue("sample.apk".isSupportedArchiveFileName())
        assertTrue("sample.rar".isSupportedArchiveFileName())
        assertTrue("sample.r00".isSupportedArchiveFileName())
        assertTrue("sample.part2.rar".isSupportedArchiveFileName())
        assertTrue("sample.7z.001".isSupportedArchiveFileName())
    }

    @Test
    fun `archive file name detection rejects unrelated extensions`() {
        assertFalse("sample.r0".isSupportedArchiveFileName())
        assertFalse("sample.r123".isSupportedArchiveFileName())
        assertFalse("sample.7z.01".isSupportedArchiveFileName())
        assertFalse("sample.txt".isSupportedArchiveFileName())
    }

    @Test
    fun `common external archive mime aliases are supported`() {
        assertTrue("application/x-gzip".asMimeType().isSupportedArchive)
        assertTrue("application/x-rar-compressed".asMimeType().isSupportedArchive)
        assertTrue("application/x-zip-compressed".asMimeType().isSupportedArchive)
        assertTrue("application/x-zstd".asMimeType().isSupportedArchive)
    }
}
