// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os

import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.provider.root.RootPosixFileStore
import com.wisso.wizefiles.provider.root.RootablePosixFileStore
import com.wisso.wizefiles.util.readParcelable

internal class LinuxFileStore private constructor(
    private val path: LinuxPath,
    private val localFileStore: LocalLinuxFileStore
) : RootablePosixFileStore(path, localFileStore, { RootPosixFileStore(it) }) {
    constructor(path: LinuxPath) : this(path, LocalLinuxFileStore(path))

    private constructor(source: Parcel) : this(source.readParcelable()!!, source.readParcelable()!!)

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(path, flags)
        dest.writeParcelable(localFileStore, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<LinuxFileStore> {
            override fun createFromParcel(source: Parcel): LinuxFileStore = LinuxFileStore(source)

            override fun newArray(size: Int): Array<LinuxFileStore?> = arrayOfNulls(size)
        }
    }
}
