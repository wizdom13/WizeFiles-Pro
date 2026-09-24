// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcel
import android.os.Parcelable
import java.nio.file.attribute.GroupPrincipal

class PosixGroup : PosixPrincipal, GroupPrincipal {
    constructor(id: Int, name: ByteString?) : super(id, name)
    private constructor(parcel: Parcel) : super(parcel)

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PosixGroup> = object : Parcelable.Creator<PosixGroup> {
            override fun createFromParcel(parcel: Parcel) = PosixGroup(parcel)
            override fun newArray(size: Int): Array<PosixGroup?> = arrayOfNulls(size)
        }
    }
}
