package com.wisso.wizefiles.feature.details.permission

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDialogArgumentsSourceTest {

    @Test
    fun permissionDialogs_doNotCastFileMetadataToPosixAttributes() {
        val principalDialog = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/details/permission/SetPrincipalDialogFragment.kt"
        )
        val modeDialog = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/details/permission/SetModeDialogFragment.kt"
        )
        val seLinuxDialog = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/details/permission/SetSeLinuxContextDialogFragment.kt"
        )

        assertFalse(principalDialog.contains("args.file.attributes as PosixFileAttributes"))
        assertFalse(modeDialog.contains("args.file.attributes as PosixFileAttributes"))
        assertFalse(seLinuxDialog.contains("args.file.attributes as PosixFileAttributes"))
    }

    @Test
    fun permissionTab_passesExplicitPosixValuesToDialogs() {
        val permissionTab = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/details/permission/FilePropertiesPermissionTabFragment.kt"
        )

        assertTrue(permissionTab.contains("SetOwnerDialogFragment.show("))
        assertTrue(permissionTab.contains("ownerId = it.id"))
        assertTrue(permissionTab.contains("SetGroupDialogFragment.show("))
        assertTrue(permissionTab.contains("groupId = it.id"))
        assertTrue(permissionTab.contains("SetModeDialogFragment.show("))
        assertTrue(permissionTab.contains("mode = mode"))
        assertTrue(permissionTab.contains("SetSeLinuxContextDialogFragment.show("))
        assertTrue(permissionTab.contains("seLinuxContext = seLinuxContext.toString()"))
    }

    private fun sourceFile(path: String): String {
        val file = listOf(File(path), File("app/$path")).firstOrNull { it.exists() }
            ?: error("Unable to locate source file for $path")
        return file.readText()
    }
}
