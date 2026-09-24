// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.editor

import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveEditCapabilitiesTest {
    @Test fun `v1 formats are editable`() {
        assertEquals(EditableArchiveFormat.ZIP, ArchiveEditCapabilities.forArchiveFile(Paths.get("a.zip")).format)
        assertEquals(EditableArchiveFormat.SEVEN_Z, ArchiveEditCapabilities.forArchiveFile(Paths.get("a.7z")).format)
        assertEquals(EditableArchiveFormat.TAR_XZ, ArchiveEditCapabilities.forArchiveFile(Paths.get("a.tar.xz")).format)
    }

    @Test fun `signed and split archives stay read only`() {
        assertFalse(ArchiveEditCapabilities.forArchiveFile(Paths.get("app.apk")).editable)
        assertFalse(ArchiveEditCapabilities.forArchiveFile(Paths.get("backup.z01")).editable)
        assertFalse(ArchiveEditCapabilities.forArchiveFile(Paths.get("backup.7z.001")).editable)
    }

    @Test fun `ordinary zip is editable`() {
        assertTrue(ArchiveEditCapabilities.forArchiveFile(Paths.get("notes.zip")).editable)
    }
}
