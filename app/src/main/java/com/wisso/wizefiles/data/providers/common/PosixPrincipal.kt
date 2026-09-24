// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.util.readParcelable
import java.security.Principal

abstract class PosixPrincipal(
    val id: Int,
    name: ByteString?
) : Principal, Parcelable {
    private val encodedName = name

    final override fun getName(): String? = encodedName?.toString()

    final override fun equals(other: Any?): Boolean = when {
        other === this -> true
        other == null || other.javaClass !== javaClass -> false
        else -> {
            other as PosixPrincipal
            id == other.id && encodedName == other.encodedName
        }
    }

    final override fun hashCode(): Int = 31 * id + (encodedName?.hashCode() ?: 0)

    protected constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        name = parcel.readParcelable()
    )

    final override fun describeContents(): Int = 0

    final override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeParcelable(encodedName, flags)
    }
}
