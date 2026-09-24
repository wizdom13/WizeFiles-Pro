// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.imageloader.coil

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidPackageIconReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val reader = AndroidPackageIconReader()

    @Test
    fun `classifies every supported package container extension case insensitively`() {
        assertEquals(AndroidPackageArchiveKind.AAB, AndroidPackageArchiveKind.fromPath("app.AAB"))
        assertEquals(AndroidPackageArchiveKind.APKS, AndroidPackageArchiveKind.fromPath("app.apks"))
        assertEquals(AndroidPackageArchiveKind.XAPK, AndroidPackageArchiveKind.fromPath("app.xApK"))
        assertEquals(AndroidPackageArchiveKind.APKM, AndroidPackageArchiveKind.fromPath("app.apkm"))
        assertEquals(null, AndroidPackageArchiveKind.fromPath("app.zip"))
    }

    @Test
    fun `APKS prefers universal APK over configuration splits`() {
        val file = zip(
            "splits/config.en.apk" to byteArrayOf(1),
            "standalones/universal.apk" to byteArrayOf(2),
            "splits/base-master.apk" to byteArrayOf(3)
        )

        assertEquals(
            AndroidPackageIconPayload.NestedApk("standalones/universal.apk"),
            reader.find(file, AndroidPackageArchiveKind.APKS)
        )
    }

    @Test
    fun `XAPK and APKM prefer their base APK`() {
        val file = zip(
            "split_config.arm64_v8a.apk" to byteArrayOf(1),
            "base.apk" to byteArrayOf(2)
        )

        assertEquals(
            AndroidPackageIconPayload.NestedApk("base.apk"),
            reader.find(file, AndroidPackageArchiveKind.XAPK)
        )
        assertEquals(
            AndroidPackageIconPayload.NestedApk("base.apk"),
            reader.find(file, AndroidPackageArchiveKind.APKM)
        )
    }

    @Test
    fun `metadata may identify a nonstandard base APK name`() {
        val file = zip(
            "manifest.json" to "{\"file\":\"game-main.apk\"}".toByteArray(),
            "game-main.apk" to byteArrayOf(1),
            "split_config.en.apk" to byteArrayOf(2)
        )

        assertEquals(
            AndroidPackageIconPayload.NestedApk("game-main.apk"),
            reader.find(file, AndroidPackageArchiveKind.XAPK)
        )
    }

    @Test
    fun `AAB chooses a high density launcher raster and ignores unrelated art`() {
        val file = zip(
            "base/manifest/AndroidManifest.xml" to "ic_launcher".toByteArray(),
            "base/res/drawable/banner.png" to byteArrayOf(1),
            "base/res/mipmap-mdpi/ic_launcher.png" to byteArrayOf(2),
            "base/res/mipmap-xxxhdpi/ic_launcher.webp" to byteArrayOf(3)
        )

        assertEquals(
            AndroidPackageIconPayload.Raster("base/res/mipmap-xxxhdpi/ic_launcher.webp"),
            reader.find(file, AndroidPackageArchiveKind.AAB)
        )
    }

    @Test
    fun `rejects traversal and case folded duplicate entry names`() {
        assertThrows(IllegalArgumentException::class.java) {
            reader.find(zip("../base.apk" to byteArrayOf(1)), AndroidPackageArchiveKind.APKM)
        }
        val duplicate = temporaryFolder.newFile("duplicate.apks")
        ZipOutputStream(duplicate.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("Base.apk"))
            output.write(1)
            output.closeEntry()
            output.putNextEntry(ZipEntry("base.apk"))
            output.write(2)
            output.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            reader.find(duplicate, AndroidPackageArchiveKind.APKS)
        }
    }

    @Test
    fun `returns no payload when a container has no usable icon source`() {
        assertEquals(
            null,
            reader.find(zip("readme.txt" to byteArrayOf(1)), AndroidPackageArchiveKind.AAB)
        )
        assertTrue(
            reader.find(zip("readme.txt" to byteArrayOf(1)), AndroidPackageArchiveKind.APKS) == null
        )
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val file = temporaryFolder.newFile()
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }
}
