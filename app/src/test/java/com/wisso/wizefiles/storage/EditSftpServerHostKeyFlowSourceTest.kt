package com.wisso.wizefiles.storage

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSftpServerHostKeyFlowSourceTest {
    @Test
    fun `connect and add flow handles unknown host key with explicit trust dialog`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/storage/EditSftpServerFragment.kt")

        assertTrue(source.contains("storage_edit_sftp_server_host_key_unknown_title"))
        assertTrue(source.contains("SftpUnknownHostKeyException"))
        assertTrue(source.contains("viewModel.trustHostKeyOnceAndConnect"))
        assertTrue(source.contains("viewModel.trustHostKeyAndSaveAndConnect"))
    }

    @Test
    fun `connect and add flow blocks host key mismatch`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/storage/EditSftpServerFragment.kt")

        assertTrue(source.contains("storage_edit_sftp_server_host_key_mismatch_title"))
        assertTrue(source.contains("SftpHostKeyMismatchException"))
    }

    @Test
    fun `connect and add flow avoids raw throwable dump for generic errors`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/storage/EditSftpServerFragment.kt")

        assertTrue(source.contains("storage_edit_sftp_server_connect_error_generic"))
    }

    private fun sourceFile(relativePath: String): String =
        File("$PROJECT_ROOT/$relativePath").readText()

    companion object {
        private const val PROJECT_ROOT = "."
    }
}
