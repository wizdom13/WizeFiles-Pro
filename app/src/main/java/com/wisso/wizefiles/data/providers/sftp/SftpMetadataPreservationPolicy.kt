package com.wisso.wizefiles.provider.sftp

import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.MetadataPreservation
import com.wisso.wizefiles.storage.MetadataPreservationReport
import com.wisso.wizefiles.storage.PreservationStatus

internal object SftpMetadataPreservationPolicy {
    fun report(copyAttributes: Boolean, failure: String?): MetadataPreservationReport {
        val requestedStatus = when {
            !copyAttributes -> PreservationStatus.NOT_REQUESTED
            failure != null -> PreservationStatus.FAILED
            else -> PreservationStatus.PRESERVED
        }
        val detail = failure?.take(MetadataPreservation.MAX_DETAIL_LENGTH)
        val alwaysPreserved = if (failure == null) PreservationStatus.PRESERVED else PreservationStatus.FAILED
        return MetadataPreservationReport(
            listOf(
                MetadataPreservation(MetadataAttribute.MODIFIED_TIME, alwaysPreserved, detail),
                MetadataPreservation(MetadataAttribute.ACCESS_TIME, requestedStatus, detail),
                MetadataPreservation(MetadataAttribute.POSIX_PERMISSIONS, alwaysPreserved, detail),
                MetadataPreservation(MetadataAttribute.OWNER, requestedStatus, detail),
                MetadataPreservation(
                    MetadataAttribute.CREATION_TIME,
                    if (copyAttributes) PreservationStatus.UNSUPPORTED else PreservationStatus.NOT_REQUESTED,
                    if (copyAttributes) "SFTP creation-time copy is unsupported" else null
                ),
                MetadataPreservation(
                    MetadataAttribute.EXTENDED_ATTRIBUTES,
                    if (copyAttributes) PreservationStatus.UNSUPPORTED else PreservationStatus.NOT_REQUESTED,
                    if (copyAttributes) "SFTP extended-attribute copy is unsupported" else null
                )
            )
        )
    }
}
