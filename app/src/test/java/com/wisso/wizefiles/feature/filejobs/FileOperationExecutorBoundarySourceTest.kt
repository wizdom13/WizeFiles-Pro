package com.wisso.wizefiles.feature.filejobs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationExecutorBoundarySourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `scanner and notification state are outside operation executor`() {
        val executor = source("FileOperationExecutor.kt")
        val scanner = source("FileOperationScanner.kt")
        val notifications = source("FileOperationNotifications.kt")

        assertTrue("internal fun FileOperationJob.scan" in scanner)
        assertTrue("internal class ScanInfo" in scanner)
        assertTrue("LocalTreeTraverser.walkPostOrder" in scanner)
        assertTrue("internal fun FileOperationJob.postScanNotification" in notifications)
        assertTrue("internal class TransferInfo" in notifications)
        assertTrue("postTransferSizeNotification" in notifications)
        assertFalse("internal class ScanInfo" in executor)
        assertFalse("internal class TransferInfo" in executor)
    }

    private fun source(name: String): String = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/feature/filejobs/$name"
    ).readText()
}
