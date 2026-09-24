// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.model

import android.os.Parcel
import com.wisso.wizefiles.storage.FileMetadata
import kotlinx.parcelize.Parceler

object FileMetadataParceler : Parceler<FileMetadata> {
    override fun create(parcel: Parcel): FileMetadata =
        FileMetadata(
            isDirectory = parcel.readInt() != 0,
            sizeBytes = parcel.readNullableLong(),
            lastModifiedEpochMillis = parcel.readNullableLong(),
            isSymbolicLink = parcel.readInt() != 0
        )

    override fun FileMetadata.write(parcel: Parcel, flags: Int) {
        parcel.writeInt(if (isDirectory) 1 else 0)
        parcel.writeNullableLong(sizeBytes)
        parcel.writeNullableLong(lastModifiedEpochMillis)
        parcel.writeInt(if (isSymbolicLink) 1 else 0)
    }
}

object NullableFileMetadataParceler : Parceler<FileMetadata?> {
    override fun create(parcel: Parcel): FileMetadata? =
        if (parcel.readInt() != 0) FileMetadataParceler.create(parcel) else null

    override fun FileMetadata?.write(parcel: Parcel, flags: Int) {
        if (this == null) {
            parcel.writeInt(0)
        } else {
            parcel.writeInt(1)
            with(FileMetadataParceler) { this@write.write(parcel, flags) }
        }
    }
}

private fun Parcel.readNullableLong(): Long? =
    if (readInt() != 0) readLong() else null

private fun Parcel.writeNullableLong(value: Long?) {
    if (value == null) {
        writeInt(0)
    } else {
        writeInt(1)
        writeLong(value)
    }
}
