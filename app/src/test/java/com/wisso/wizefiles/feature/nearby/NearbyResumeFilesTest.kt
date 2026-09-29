// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class NearbyResumeFilesTest {
    @Test fun resumeAfterPauseBetweenCheckpointsKeepsPrefixAndReplacesUnacknowledgedTail() {
        val file = Files.createTempFile("nearby-resume", ".part")
        try {
            val source = ByteArray(1024) { (it * 23).toByte() }
            Files.write(file, source.copyOf(800))
            prepareNearbyResumeFile(file, 512)
            assertArrayEquals(source.copyOf(512), Files.readAllBytes(file))
            Files.write(file, source.copyOfRange(512, source.size), StandardOpenOption.APPEND)
            assertArrayEquals(source, Files.readAllBytes(file))
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun missingAcknowledgedBytesCannotBeSilentlyPadded() {
        val file = Files.createTempFile("nearby-resume", ".part")
        try {
            Files.write(file, byteArrayOf(1, 2))
            assertThrows(IllegalArgumentException::class.java) { prepareNearbyResumeFile(file, 3) }
            assertArrayEquals(byteArrayOf(1, 2), Files.readAllBytes(file))
        } finally { Files.deleteIfExists(file) }
    }
}
