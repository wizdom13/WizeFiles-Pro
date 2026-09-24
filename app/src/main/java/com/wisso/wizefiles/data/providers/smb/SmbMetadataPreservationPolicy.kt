// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb

import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.MetadataPreservation
import com.wisso.wizefiles.storage.MetadataPreservationReport
import com.wisso.wizefiles.storage.PreservationStatus

internal object SmbMetadataPreservationPolicy {
    fun report(copyAttributes: Boolean, basicMetadataFailure: String?): MetadataPreservationReport {
        val basicStatus = when {
            !copyAttributes -> PreservationStatus.NOT_REQUESTED
            basicMetadataFailure != null -> PreservationStatus.FAILED
            else -> PreservationStatus.PRESERVED
        }
        val detail = basicMetadataFailure?.take(MetadataPreservation.MAX_DETAIL_LENGTH)
        val unsupportedStatus = if (copyAttributes) PreservationStatus.UNSUPPORTED else PreservationStatus.NOT_REQUESTED
        return MetadataPreservationReport(
            listOf(
                MetadataPreservation(MetadataAttribute.CREATION_TIME, basicStatus, detail),
                MetadataPreservation(MetadataAttribute.MODIFIED_TIME, basicStatus, detail),
                MetadataPreservation(MetadataAttribute.ACCESS_TIME, basicStatus, detail),
                MetadataPreservation(MetadataAttribute.DOS_ATTRIBUTES, basicStatus, detail),
                MetadataPreservation(MetadataAttribute.ACL, unsupportedStatus, unsupportedDetail(copyAttributes, "security descriptor")),
                MetadataPreservation(MetadataAttribute.EXTENDED_ATTRIBUTES, unsupportedStatus, unsupportedDetail(copyAttributes, "extended attribute"))
            )
        )
    }

    private fun unsupportedDetail(requested: Boolean, name: String): String? =
        if (requested) "SMB $name copy is unsupported" else null
}
