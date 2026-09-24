// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BundletoolTocParserTest {
    private val parser = BundletoolTocParser()

    @Test
    fun `reads package and APK paths from variants and asset slices`() {
        val variant = message(2, apkSet("splits/base-master.apk", "splits/config.en.apk"))
        val assetSlice = apkSet("asset-slices/pack.apk")
        val toc = message(1, variant) + message(3, assetSlice) +
            string(4, "com.example.app")

        val inventory = parser.parse(toc)

        assertEquals("com.example.app", inventory.packageName)
        assertEquals(
            setOf("splits/base-master.apk", "splits/config.en.apk", "asset-slices/pack.apk"),
            inventory.apkPaths
        )
    }

    @Test
    fun `rejects traversal duplicate and malformed protobuf paths`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(toc("../base.apk"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                message(1, message(2, apkSet("base.apk", "base.apk"))) +
                    string(4, "com.example")
            )
        }
        assertThrows(Exception::class.java) {
            parser.parse(byteArrayOf(0x0A, 0x7F))
        }
    }

    private fun toc(path: String): ByteArray =
        message(1, message(2, apkSet(path))) + string(4, "com.example")

    private fun apkSet(vararg paths: String): ByteArray = paths.fold(ByteArray(0)) { bytes, path ->
        bytes + message(2, string(2, path))
    }

    private fun string(field: Int, value: String): ByteArray =
        message(field, value.toByteArray(Charsets.UTF_8))

    private fun message(field: Int, value: ByteArray): ByteArray = ByteArrayOutputStream().run {
        writeVarint((field shl 3 or 2).toLong())
        writeVarint(value.size.toLong())
        write(value)
        toByteArray()
    }

    private fun ByteArrayOutputStream.writeVarint(input: Long) {
        var value = input
        while (value and -128L != 0L) {
            write((value.toInt() and 0x7F) or 0x80)
            value = value ushr 7
        }
        write(value.toInt())
    }
}
