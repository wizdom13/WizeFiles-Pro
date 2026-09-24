package com.wisso.wizefiles.provider.common

import android.os.Parcel
import android.os.Parcelable

class ParcelablePosixFileMode(mode: Set<PosixFileModeBit>) : Parcelable {
    val value: Set<PosixFileModeBit> = mode.toSet()

    private constructor(parcel: Parcel) : this(PosixFileMode.fromInt(parcel.readInt()))

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(value.toInt())
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ParcelablePosixFileMode> =
            object : Parcelable.Creator<ParcelablePosixFileMode> {
                override fun createFromParcel(parcel: Parcel) = ParcelablePosixFileMode(parcel)
                override fun newArray(size: Int): Array<ParcelablePosixFileMode?> =
                    arrayOfNulls(size)
            }
    }
}

fun Set<PosixFileModeBit>.toParcelable(): ParcelablePosixFileMode =
    ParcelablePosixFileMode(this)
