package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncScanDiagnosticsTest {
    @Test
    fun `scan diagnostics retain side endpoint and provider error`() {
        val source = SyncScanResult(
            rootUri = "file:///source",
            storageIdentity = "source",
            entries = emptyList(),
            errors = emptyList()
        )
        val destination = SyncScanResult(
            rootUri = "ftp://dlpuser@ftp.dlptest.com/",
            storageIdentity = "ftp",
            entries = emptyList(),
            errors = listOf("/: FileSystemException — FTP data connection was refused")
        )

        val decoded = decodeSyncScanDiagnostics(
            encodeSyncScanDiagnostics(source, destination, null)
        )

        assertEquals(1, decoded.size)
        assertEquals(SyncSide.DESTINATION, decoded.single().side)
        assertEquals(destination.rootUri, decoded.single().endpointUri)
        assertTrue(decoded.single().detail.contains("FTP data connection was refused"))
    }

    @Test
    fun `control characters cannot corrupt persisted diagnostics`() {
        val source = SyncScanResult(
            rootUri = "file:///source\tname",
            storageIdentity = "source",
            entries = emptyList(),
            errors = listOf("folder\nAccessDeniedException")
        )
        val destination = SyncScanResult(
            rootUri = "file:///destination",
            storageIdentity = "destination",
            entries = emptyList(),
            errors = emptyList()
        )

        val decoded = decodeSyncScanDiagnostics(
            encodeSyncScanDiagnostics(source, destination, null)
        ).single()

        assertFalse(decoded.endpointUri.contains('\t'))
        assertFalse(decoded.detail.contains('\n'))
    }

    @Test
    fun `only non-overridable scan blocks use retry action`() {
        assertTrue(isRetryableSyncBlock("SCAN_INCOMPLETE"))
        assertTrue(isRetryableSyncBlock("STORAGE_IDENTITY_CHANGED"))
        assertFalse(isRetryableSyncBlock("SOURCE_UNEXPECTEDLY_EMPTY"))
        assertFalse(isRetryableSyncBlock(""))
    }
}
