// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os.syscall

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelinuxBridgeMigrationSourceTest {

    @Test
    fun syscallRoutesSelinuxCallsThroughInternalBridge() {
        val syscall = readProjectFile(
            "src/main/java/com/wisso/wizefiles/data/providers/os/syscall/Syscall.kt",
            "app/src/main/java/com/wisso/wizefiles/data/providers/os/syscall/Syscall.kt"
        )

        assertTrue(syscall.contains("import com.wisso.wizefiles.core.selinux.SeLinuxBridge"))
        assertTrue(syscall.contains("SeLinuxBridge.isSelinuxEnabled()"))
        assertTrue(syscall.contains("SeLinuxBridge.getEnforce()"))
        assertTrue(syscall.contains("SeLinuxBridge.getFileContext"))
        assertTrue(syscall.contains("SeLinuxBridge.lGetFileContext"))
        assertTrue(syscall.contains("SeLinuxBridge.setFileContext"))
        assertTrue(syscall.contains("SeLinuxBridge.lSetFileContext"))
        assertTrue(syscall.contains("SeLinuxBridge.restoreContext(path.borrowBytes(), flags)"))
        assertFalse(syscall.contains("SELinuxCompat.native_restorecon"))
        assertFalse(syscall.contains("me.zhanghai.android.libselinux.SeLinux"))
    }

    @Test
    fun projectNoLongerDependsOnExternalLibselinux() {
        val gradle = readProjectFile("build.gradle", "app/build.gradle")

        assertFalse(gradle.contains("me.zhanghai.android.libselinux:library"))
    }

    @Test
    fun internalSelinuxBridgeIsBackedByJniSource() {
        val bridge = readProjectFile(
            "src/main/java/com/wisso/wizefiles/core/selinux/SeLinuxBridge.kt",
            "app/src/main/java/com/wisso/wizefiles/core/selinux/SeLinuxBridge.kt"
        )
        val cmake = readProjectFile("CMakeLists.txt", "app/CMakeLists.txt")

        assertTrue(bridge.contains("System.loadLibrary(\"selinuxbridge\")"))
        assertTrue(bridge.contains("external fun isSelinuxEnabled"))
        assertTrue(bridge.contains("external fun getEnforce"))
        assertTrue(bridge.contains("external fun getFileContext"))
        assertTrue(bridge.contains("external fun lGetFileContext"))
        assertTrue(bridge.contains("external fun setFileContext"))
        assertTrue(bridge.contains("external fun lSetFileContext"))
        assertTrue(bridge.contains("external fun restoreContext"))
        assertTrue(cmake.contains("add_library(selinuxbridge SHARED"))
        assertTrue(cmake.contains("src/main/jni/selinux_bridge.c"))
        val native = readProjectFile("src/main/jni/selinux_bridge.c", "app/src/main/jni/selinux_bridge.c")
        assertTrue(native.contains("selinux_android_restorecon"))
    }

    private fun readProjectFile(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Unable to locate any of: ${candidates.joinToString()}")
        return file.readText()
    }
}
