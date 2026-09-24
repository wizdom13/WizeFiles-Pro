// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedContainerSourceContractTest {
    @Test
    fun `official sevenzip source is pinned and built as a read backend`() {
        val cmake = projectFile("app/CMakeLists.txt")
        assertTrue(cmake.contains("https://7-zip.org/a/7z2602-src.tar.xz"))
        assertTrue(cmake.contains("SHA256=cf967c98bca02a4b8b16375f441825a8e141362f14be1969bbec8e1ca0bff9dd"))
        assertTrue(cmake.contains("Format7zF"))
        assertTrue(cmake.contains("add_library(sevenzip-jni SHARED"))
        assertTrue(cmake.contains("sevenzip_jni/sevenzip_jni.cpp"))
        assertFalse(cmake.contains("sevenzipjbinding"))
    }

    @Test
    fun `specialist backend is fallback only and cannot create archives`() {
        val reader = projectFile(
            "app/src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/ArchiveReader.kt"
        )
        val native = projectFile(
            "app/src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/SevenZipNative.kt"
        )
        val writer = projectFile(
            "app/src/main/java/com/wisso/wizefiles/data/providers/archive/archiver/ArchiveWriter.kt"
        )
        assertTrue(reader.indexOf("readEntriesWithLibarchive") < reader.indexOf("SevenZipNative.readEntries"))
        assertTrue(reader.contains("ArchiveReadBackend.SEVEN_ZIP"))
        assertTrue(native.contains("private external fun list"))
        assertTrue(native.contains("private external fun extract"))
        assertFalse(native.contains("external fun create"))
        assertFalse(writer.contains("SevenZipNative"))
    }

    @Test
    fun `native bridge enforces adjacent volumes and bounded extraction`() {
        val native = projectFile("app/src/main/cpp/sevenzip_jni/sevenzip_jni.cpp")
        assertTrue(native.contains("constexpr UInt32 kMaxItems = 10000"))
        assertTrue(native.contains("fileName.find('/')"))
        assertTrue(native.contains("fileName.find('\\\\')"))
        assertTrue(native.contains("O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600"))
        assertTrue(native.contains("size > limit_ - written_"))
        assertTrue(native.contains("unlink("))
    }

    @Test
    fun `native provenance lists both upstream and local bridge`() {
        val manifest = projectFile("app/src/main/cpp/native-dependencies.json")
        assertTrue(manifest.contains("\"name\": \"7-Zip\""))
        assertTrue(manifest.contains("\"version\": \"26.02\""))
        assertTrue(manifest.contains("cf967c98bca02a4b8b16375f441825a8e141362f14be1969bbec8e1ca0bff9dd"))
        assertTrue(manifest.contains("sevenzip-jni"))
    }

    private fun projectFile(path: String): String {
        val direct: Path = Paths.get(path)
        val resolved = if (Files.exists(direct)) direct else Paths.get("..", path)
        return String(Files.readAllBytes(resolved), StandardCharsets.UTF_8)
    }
}
