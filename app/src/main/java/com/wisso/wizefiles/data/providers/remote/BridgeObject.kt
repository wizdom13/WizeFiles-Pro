// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.util.readParcelable

class BridgeObject(value: Any) : Parcelable {
    private val payload: Parcelable =
        value as? Parcelable
            ?: throw IllegalArgumentException("Bridge values must implement Parcelable")

    val value: Any
        get() = payload

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> value(): T = payload as T

    private constructor(parcel: Parcel) : this(
        parcel.readParcelable<Parcelable>()
            ?: throw BadParcelableException("Missing bridge object payload")
    )

    override fun describeContents(): Int = payload.describeContents()

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(payload, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BridgeObject> = object : Parcelable.Creator<BridgeObject> {
            override fun createFromParcel(parcel: Parcel) = BridgeObject(parcel)
            override fun newArray(size: Int): Array<BridgeObject?> = arrayOfNulls(size)
        }
    }
}

fun Any.toParcelable(): BridgeObject = BridgeObject(this)
