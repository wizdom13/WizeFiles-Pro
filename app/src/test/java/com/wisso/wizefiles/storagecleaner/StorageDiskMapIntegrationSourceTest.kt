package com.wisso.wizefiles.storagecleaner

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageDiskMapIntegrationSourceTest {
    @Test
    fun `cleanup card sits between composition summary and recommendations`() {
        val layout = sourceFile("app/src/main/res/layout/activity_storage_cleaner.xml")

        val summary = layout.indexOf("@+id/categorySummaryContainer")
        val diskMap = layout.indexOf("@+id/visualDiskMapCard")
        val recommendations = layout.indexOf("@+id/recommendationsRecycler")

        assertTrue(summary >= 0)
        assertTrue(diskMap > summary)
        assertTrue(recommendations > diskMap)
    }

    @Test
    fun `cleanup card opens the dedicated cached disk map screen`() {
        val activity = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerActivity.kt"
        )
        val manifest = sourceFile("app/src/main/AndroidManifest.xml")

        assertTrue(activity.contains("binding.visualDiskMapCard.setOnClickListener"))
        assertTrue(activity.contains("Intent(this, StorageDiskMapActivity::class.java)"))
        assertTrue(activity.contains("binding.visualDiskMapCard.isVisible = categories.any"))
        assertTrue(manifest.contains("com.wisso.wizefiles.storagecleaner.StorageDiskMapActivity"))
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        val moduleRelative = File(path.removePrefix("app/"))
        return listOf(direct, moduleRelative).firstOrNull { it.exists() }?.readText()
            ?: error("Unable to locate $path")
    }
}
