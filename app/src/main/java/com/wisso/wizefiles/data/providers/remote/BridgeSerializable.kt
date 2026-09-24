package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.core.android.compat.readSerializableCompat
import java.io.Serializable

class BridgeSerializable(private val payload: Serializable) : Parcelable {
    @Suppress("UNCHECKED_CAST")
    fun <T> value(): T = payload as T

    private constructor(parcel: Parcel) : this(
        parcel.readSerializableCompat<Serializable>()
            ?: throw BadParcelableException("Missing serializable bridge payload")
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(payload)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BridgeSerializable> =
            object : Parcelable.Creator<BridgeSerializable> {
                override fun createFromParcel(parcel: Parcel) = BridgeSerializable(parcel)
                override fun newArray(size: Int): Array<BridgeSerializable?> = arrayOfNulls(size)
            }
    }
}

fun Serializable.toParcelable(): BridgeSerializable = BridgeSerializable(this)
