// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcel
import android.os.Parcelable
import java.nio.file.attribute.FileTime
import com.wisso.wizefiles.core.android.compat.readSerializableCompat

class ParcelableFileTime(val value: FileTime) : Parcelable {
    private constructor(source: Parcel) : this(FileTime.from(source.readSerializableCompat()))

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(value.toInstant())
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<ParcelableFileTime> {
            override fun createFromParcel(source: Parcel): ParcelableFileTime =
                ParcelableFileTime(source)

            override fun newArray(size: Int): Array<ParcelableFileTime?> = arrayOfNulls(size)
        }
    }
}

fun FileTime.toParcelable(): ParcelableFileTime = ParcelableFileTime(this)
