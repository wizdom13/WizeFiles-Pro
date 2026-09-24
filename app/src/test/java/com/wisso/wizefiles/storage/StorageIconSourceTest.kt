package com.wisso.wizefiles.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageIconSourceTest {

    @Test
    fun `connect storage entries use the requested order and icon adapter`() {
        val source = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/AddStorageDialogFragment.kt"
        ).readText()
        val orderedKeys = listOf(
            "storage_add_storage_external_drive",
            "storage_add_storage_rclone",
            "storage_add_storage_saf_folder",
            "storage_add_storage_android_data",
            "storage_add_storage_android_obb",
            "storage_add_storage_ftp_server",
            "storage_add_storage_sftp_server",
            "storage_add_storage_smb_server"
        )

        val positions = orderedKeys.map(source::indexOf)
        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
        assertTrue(source.contains("setAdapter(StorageEntryAdapter"))
        assertTrue(source.contains("setImageResource(entry.iconRes)"))
    }

    @Test
    fun `featured cloud providers have distinct vector icons`() {
        val providerTypes = listOf(
            "drive", "onedrive", "dropbox", "box", "pcloud", "mega", "webdav", "s3"
        )
        val iconIds = providerTypes.map(::rcloneProviderIconRes)

        assertEquals(providerTypes.size, iconIds.distinct().size)
        iconIds.forEach { iconId ->
            assertNotEquals(com.wisso.wizefiles.R.drawable.ic_cloud_white_24dp, iconId)
        }
        assertEquals(
            com.wisso.wizefiles.R.drawable.ic_cloud_white_24dp,
            rcloneProviderIconRes("unknown-provider")
        )
    }

    @Test
    fun `cloud provider dropdown binds provider and action icons`() {
        val source = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/EditRcloneStorageFragment.kt"
        ).readText() + projectFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/RcloneProviderChoices.kt"
        ).readText()

        assertTrue(source.contains("ProviderChoiceAdapter(requireContext(), providerChoices)"))
        assertTrue(source.contains("rcloneProviderIconRes(choice.backendType.orEmpty())"))
        assertTrue(source.contains("R.drawable.ic_download_white_24dp"))
        assertTrue(source.contains("R.drawable.ic_more_horizontal_white_24dp"))
        assertTrue(source.contains("setImageResource(iconRes)"))
        assertTrue(
            source.contains(
                "binding.providerLayout.setStartIconTintList(binding.providerEdit.textColors)"
            )
        )
        assertTrue(source.contains("binding.providerLayout.setStartIconDrawable("))
    }

    @Test
    fun `connect cloud drive uses the polished title`() {
        val strings = projectFile("src/main/res/values/strings.xml").readText()
        assertTrue(
            strings.contains(
                "<string name=\"storage_add_storage_rclone\">Connect Cloud Drive</string>"
            )
        )
    }

    private fun projectFile(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot
        throw java.io.FileNotFoundException(path)
    }
}
