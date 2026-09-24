// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import com.wisso.wizefiles.core.app.appClassLoader
import com.wisso.wizefiles.core.android.compat.writeParcelableListCompat
import com.wisso.wizefiles.util.readParcelable

object BundleParceler : Parceler<Bundle?> {
    override fun create(parcel: Parcel): Bundle? = parcel.readBundle(appClassLoader)

    override fun Bundle?.write(parcel: Parcel, flags: Int) {
        parcel.writeBundle(this)
    }
}

object ParcelableParceler : Parceler<Any?> {
    override fun create(parcel: Parcel): Any? = parcel.readParcelable<Parcelable>()

    override fun Any?.write(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(this as Parcelable?, flags)
    }
}

object ParcelableListParceler : Parceler<List<Any?>> {
    override fun create(parcel: Parcel): List<Any?> =
        parcel.readParcelableListCompat(appClassLoader)

    override fun List<Any?>.write(parcel: Parcel, flags: Int) {
        @Suppress("UNCHECKED_CAST")
        parcel.writeParcelableListCompat(this as List<Parcelable?>, flags)
    }
}
