// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb

import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.PreservationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbMetadataPreservationPolicyTest {
    @Test fun `content success reports basic metadata and unsupported SMB metadata`() {
        val report = SmbMetadataPreservationPolicy.report(copyAttributes = true, basicMetadataFailure = null)
        assertEquals(PreservationStatus.PRESERVED, report.entries.single { it.attribute == MetadataAttribute.MODIFIED_TIME }.status)
        assertEquals(PreservationStatus.PRESERVED, report.entries.single { it.attribute == MetadataAttribute.DOS_ATTRIBUTES }.status)
        assertEquals(PreservationStatus.UNSUPPORTED, report.entries.single { it.attribute == MetadataAttribute.ACL }.status)
        assertEquals(PreservationStatus.UNSUPPORTED, report.entries.single { it.attribute == MetadataAttribute.EXTENDED_ATTRIBUTES }.status)
        assertFalse(report.isComplete)
    }

    @Test fun `metadata failure remains a warning after content success`() {
        val report = SmbMetadataPreservationPolicy.report(true, "server rejected timestamps")
        assertTrue(report.warnings.any { it.attribute == MetadataAttribute.MODIFIED_TIME && it.status == PreservationStatus.FAILED })
        assertTrue(report.warnings.all { !it.detail.isNullOrBlank() })
    }

    @Test fun `unrequested metadata does not create warnings`() {
        val report = SmbMetadataPreservationPolicy.report(false, null)
        assertTrue(report.warnings.isEmpty())
        assertTrue(report.entries.all { it.status == PreservationStatus.NOT_REQUESTED })
    }
}
