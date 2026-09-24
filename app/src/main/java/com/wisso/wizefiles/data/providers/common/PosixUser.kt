// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcel
import android.os.Parcelable
import java.nio.file.attribute.UserPrincipal

class PosixUser : PosixPrincipal, UserPrincipal {
    constructor(id: Int, name: ByteString?) : super(id, name)
    private constructor(parcel: Parcel) : super(parcel)

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PosixUser> = object : Parcelable.Creator<PosixUser> {
            override fun createFromParcel(parcel: Parcel) = PosixUser(parcel)
            override fun newArray(size: Int): Array<PosixUser?> = arrayOfNulls(size)
        }
    }
}
