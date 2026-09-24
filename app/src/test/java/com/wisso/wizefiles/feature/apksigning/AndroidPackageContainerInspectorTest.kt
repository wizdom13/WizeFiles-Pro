// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidPackageContainerInspectorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `recognizes and authenticates WizeFiles APKS metadata`() {
        val base = byteArrayOf(1, 2, 3)
        val split = byteArrayOf(4, 5, 6)
        val metadata = JSONObject()
            .put("formatVersion", 1)
            .put("packageName", "com.example.app")
            .put("versionCode", 42)
            .put("apks", JSONArray().apply {
                put(JSONObject().put("file", "base.apk").put("sha256", sha256(base)))
                put(JSONObject().put("file", "split_config.en.apk").put("sha256", sha256(split)))
            })
        val file = zip(
            "valid.apks",
            mapOf(
                "base.apk" to base,
                "split_config.en.apk" to split,
                "metadata.json" to metadata.toString().toByteArray()
            )
        )

        val inventory = AndroidPackageContainerInspector().inspect(file)

        assertEquals(AndroidPackageContainerFormat.WIZEFILES_APKS, inventory.format)
        assertEquals(2, inventory.apkEntries.size)
        assertEquals("com.example.app", inventory.packageNameHint)
        assertEquals(42L, inventory.versionCodeHint)
    }

    @Test
    fun `recognizes minimal AAB structure without treating it as an APK set`() {
        val file = zip(
            "valid.aab",
            mapOf(
                "BundleConfig.pb" to byteArrayOf(1),
                "base/manifest/AndroidManifest.xml" to byteArrayOf(2)
            )
        )

        val inventory = AndroidPackageContainerInspector().inspect(file)

        assertEquals(AndroidPackageContainerFormat.AAB, inventory.format)
        assertTrue(inventory.apkEntries.isEmpty())
    }

    @Test
    fun `rejects traversal duplicate aliases and checksum substitution`() {
        val inspector = AndroidPackageContainerInspector()
        assertThrows(AndroidPackageContainerException::class.java) {
            inspector.inspect(zip("traversal.apkm", mapOf("../base.apk" to byteArrayOf(1))))
        }
        assertThrows(AndroidPackageContainerException::class.java) {
            inspector.inspect(zip("duplicate.apkm", listOf(
                "base.apk" to byteArrayOf(1),
                "BASE.APK" to byteArrayOf(1)
            )))
        }
        val badMetadata = JSONObject()
            .put("packageName", "com.example.app")
            .put("versionCode", 1)
            .put("apks", JSONArray().put(
                JSONObject().put("file", "base.apk").put("sha256", "00")
            ))
        assertThrows(AndroidPackageContainerException::class.java) {
            inspector.inspect(zip("checksum.apks", mapOf(
                "base.apk" to byteArrayOf(1),
                "metadata.json" to badMetadata.toString().toByteArray()
            )))
        }
    }

    private fun zip(name: String, entries: Map<String, ByteArray>): File =
        zip(name, entries.toList())

    private fun zip(name: String, entries: List<Pair<String, ByteArray>>): File =
        temporaryFolder.newFile(name).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (entryName, bytes) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}
