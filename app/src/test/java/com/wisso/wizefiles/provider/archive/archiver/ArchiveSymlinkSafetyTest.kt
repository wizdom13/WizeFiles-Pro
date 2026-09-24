package com.wisso.wizefiles.provider.archive.archiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveSymlinkSafetyTest {
    @Test
    fun rejectsSymlinkTargetOutsideRoot() {
        assertFalse(ArchiveEntryValidator.validateSymlinkTarget("../../outside"))
    }

    @Test
    fun rejectsAbsoluteSymlinkTarget() {
        assertFalse(ArchiveEntryValidator.validateSymlinkTarget("/etc/passwd"))
    }

    @Test
    fun acceptsSafeRelativeSymlinkTarget() {
        assertTrue(ArchiveEntryValidator.validateSymlinkTarget("folder/child.txt"))
    }
}
