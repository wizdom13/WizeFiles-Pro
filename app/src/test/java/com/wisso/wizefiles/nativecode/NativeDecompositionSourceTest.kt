package com.wisso.wizefiles.nativecode

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDecompositionSourceTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "app/CMakeLists.txt").isFile }

    @Test
    fun `archive JNI keeps validation and malformed handle errors while delegating units`() {
        val main = source("src/main/cpp/libarchive_jni/archive_jni.c")
        listOf("callbacks", "reader", "writer", "metadata", "registration").forEach {
            assertTrue("archive_jni_$it.c" in main)
        }
        val metadata = source("src/main/cpp/libarchive_jni/archive_jni_metadata.c")
        assertTrue("ensure_archive_entry_handle" in metadata)
        assertTrue("archive entry handle is null" in metadata)
        assertFalse("JNI_OnLoad" in main)
    }

    @Test
    fun `syscall JNI delegates families without changing exported method names`() {
        val main = source("src/main/jni/syscall.c")
        listOf("file", "process", "xattr", "stat", "errors").forEach {
            assertTrue("syscall_$it.c" in main)
        }
        val units = listOf("file", "process", "xattr", "stat")
            .joinToString(separator = "") { source("src/main/jni/syscall_$it.c") }
        assertTrue("Java_com_wisso_wizefiles_provider_os_syscall_Syscall_access" in units)
        assertTrue("throwSyscallException" in units)
    }

    private fun source(path: String): String = File(root, "app/$path").readText()
}
