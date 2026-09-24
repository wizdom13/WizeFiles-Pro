// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os.syscall

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyscallJniHardeningSourceTest {

    @Test
    fun syscallJniNoLongerAbortsOnLookupFailures() {
        val source = readProjectFile("src/main/jni/syscall.c", "app/src/main/jni/syscall.c")

        assertFalse(source.contains("abort()"))
        assertTrue(source.contains("throwIllegalStateException"))
        assertTrue(source.contains("Failed to resolve SyscallException constructors"))
        assertTrue(source.contains("mallocEmptyString"))
    }

    @Test
    fun byteStringJniUsesBorrowApiInsteadOfPrivateStorageLayout() {
        val syscallSource = readProjectFile(
            "src/main/jni/syscall.c",
            "app/src/main/jni/syscall.c"
        )
        val byteStringSource = readProjectFile(
            "src/main/java/com/wisso/wizefiles/data/providers/common/ByteString.kt",
            "app/src/main/java/com/wisso/wizefiles/data/providers/common/ByteString.kt"
        )

        assertTrue(byteStringSource.contains("fun borrowBytes(): ByteArray = storage"))
        assertTrue(syscallSource.contains("\"borrowBytes\", \"()[B\""))
        assertTrue(syscallSource.contains("CallObjectMethod"))
        assertFalse(syscallSource.contains("getByteStringBytesField"))
        assertFalse(syscallSource.contains("\"bytes\", \"[B\""))
    }

    private fun readProjectFile(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Unable to locate any of: ${candidates.joinToString()}")
        return file.readText()
    }
}
