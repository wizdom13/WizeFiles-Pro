// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.document

import android.net.Uri
import android.os.Parcelable
import java.time.Instant
import java.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.provider.common.AbstractContentProviderFileAttributes
import com.wisso.wizefiles.provider.common.FileTimeParceler

@Parcelize
internal class DocumentFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val mimeType: String?,
    override val size: Long,
    override val fileKey: Parcelable,
    private val flags: Int
) : AbstractContentProviderFileAttributes() {
    fun flags(): Int = flags

    companion object {
        fun from(
            lastModifiedTimeMillis: Long,
            mimeType: String?,
            size: Long,
            flags: Int,
            uri: Uri
        ): DocumentFileAttributes {
            val lastModifiedTime = FileTime.from(Instant.ofEpochMilli(lastModifiedTimeMillis))
            val fileKey = uri
            return DocumentFileAttributes(lastModifiedTime, mimeType, size, fileKey, flags)
        }
    }
}
