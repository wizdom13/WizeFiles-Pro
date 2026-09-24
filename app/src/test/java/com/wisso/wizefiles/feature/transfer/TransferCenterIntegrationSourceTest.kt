// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCenterIntegrationSourceTest {
    @Test
    fun `browser exposes transfer center outside picker flows`() {
        val fragment = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val menu = sourceFile("app/src/main/res/menu/menu_file_list.xml")
        val navigation = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt"
        )
        val manifest = sourceFile("app/src/main/AndroidManifest.xml")

        assertFalse(menu.contains("@+id/action_transfer_center"))
        assertTrue(navigation.contains("NavigationAction.TRANSFER_CENTER"))
        assertTrue(fragment.contains("Intent(requireContext(), TransferCenterActivity::class.java)"))
        assertTrue(fragment.contains("override fun launchNavigationAction(action: NavigationAction)"))
        assertTrue(manifest.contains("TransferCenterActivity"))
        assertTrue(manifest.contains("TransferDetailActivity"))
    }

    @Test
    fun `boot reconciliation never starts data sync foreground service`() {
        val worker = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/transfer/TransferReconciliationWorker.kt"
        )
        assertTrue(worker.contains("WorkManager.getInstance(context).enqueueUniqueWork"))
        assertTrue(worker.contains("Transfers ready to resume").not())
        assertFalse(worker.contains("startForegroundService"))
        assertFalse(worker.contains("startService"))
    }

    @Test
    fun `persistent item transfers use temporary sibling finalization`() {
        val tracker = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/transfer/TransferItemTracker.kt"
        )
        val engine = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationTransferEngine.kt"
        )
        assertTrue(tracker.contains(".wizefiles-part-"))
        assertTrue(engine.contains("prepareTemporaryTarget"))
        assertTrue(engine.contains("transferTarget.moveTo(resolvedTarget"))
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        val moduleRelative = File(path.removePrefix("app/"))
        return listOf(direct, moduleRelative).firstOrNull(File::exists)?.readText()
            ?: error("Unable to locate $path")
    }
}
