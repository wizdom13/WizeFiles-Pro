// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.content

import android.net.Uri
import android.os.Parcelable
import java.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.provider.common.AbstractContentProviderFileAttributes
import com.wisso.wizefiles.provider.common.EPOCH
import com.wisso.wizefiles.provider.common.FileTimeParceler

@Parcelize
internal class ContentFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val mimeType: String?,
    override val size: Long,
    override val fileKey: Parcelable
) : AbstractContentProviderFileAttributes() {
    companion object {
        fun from(mimeType: String?, size: Long, uri: Uri): ContentFileAttributes {
            val lastModifiedTime = FileTime::class.EPOCH
            val fileKey = uri
            return ContentFileAttributes(lastModifiedTime, mimeType, size, fileKey)
        }
    }
}
