package com.wisso.wizefiles.provider.ftp

import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.MetadataPreservation
import com.wisso.wizefiles.storage.MetadataPreservationReport
import com.wisso.wizefiles.storage.PreservationStatus

internal object FtpMetadataPreservationPolicy {
    fun report(copyAttributes: Boolean, failure: String?): MetadataPreservationReport {
        val detail = failure?.take(MetadataPreservation.MAX_DETAIL_LENGTH)
        return MetadataPreservationReport(
            listOf(
                MetadataPreservation(
                    MetadataAttribute.MODIFIED_TIME,
                    if (failure == null) PreservationStatus.PRESERVED else PreservationStatus.FAILED,
                    detail
                ),
                unsupported(MetadataAttribute.ACCESS_TIME, "access-time", copyAttributes),
                unsupported(MetadataAttribute.CREATION_TIME, "creation-time", copyAttributes),
                unsupported(MetadataAttribute.POSIX_PERMISSIONS, "permission", copyAttributes),
                unsupported(MetadataAttribute.OWNER, "owner", copyAttributes),
                unsupported(
                    MetadataAttribute.EXTENDED_ATTRIBUTES,
                    "extended-attribute",
                    copyAttributes
                )
            )
        )
    }

    private fun unsupported(
        attribute: MetadataAttribute,
        label: String,
        requested: Boolean
    ) = MetadataPreservation(
        attribute,
        if (requested) PreservationStatus.UNSUPPORTED else PreservationStatus.NOT_REQUESTED,
        if (requested) "FTP $label copy is unsupported" else null
    )
}
