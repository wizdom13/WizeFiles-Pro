package com.wisso.wizefiles.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewIntentPermissionSourceTest {

    @Test
    fun viewIntentsDoNotAddWriteGrantInOpenFlows() {
        val fileListFragment =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt")
        val openFileActivity =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/OpenFileActivity.kt")
        val openFileAsDialog =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/OpenFileAsDialogFragment.kt")
        val fileOperationExecutor =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/filejobs/FileOpenRenameJobs.kt")
        val vaultOpenSessionManager =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/vault/VaultOpenSessionManager.kt")

        assertFalse(fileListFragment.contains("createViewIntent(mimeType)\n                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)"))
        assertFalse(openFileActivity.contains("createViewIntent(mimeType)\n                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)"))
        assertFalse(openFileAsDialog.contains("createViewIntent(mimeType)\n            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)"))
        assertFalse(fileOperationExecutor.contains("createViewIntent(mimeType)\n                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)"))
        assertFalse(vaultOpenSessionManager.contains("createViewIntent(mimeType)\n                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)"))
    }

    @Test
    fun editIntentKeepsWriteGrant() {
        val intentExtensions = sourceFile("src/main/java/com/wisso/wizefiles/util/IntentExtensions.kt")
        assertTrue(
            intentExtensions.contains(
                "Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION"
            )
        )
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) return direct.readText()
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        throw java.io.FileNotFoundException(path)
    }
}
