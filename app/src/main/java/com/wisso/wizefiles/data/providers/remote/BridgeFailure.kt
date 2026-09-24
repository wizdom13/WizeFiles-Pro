package com.wisso.wizefiles.provider.remote

import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.core.android.compat.readSerializableCompat
import java.io.IOException

class BridgeFailure() : Parcelable {
    private var storedException: Exception? = null

    var value: Exception?
        get() = storedException
        set(exception) {
            require(exception == null || exception is IOException || exception is RuntimeException) {
                "Only IO and runtime failures may cross the file bridge"
            }
            check(storedException == null) { "A bridge failure can only be assigned once" }
            storedException = exception
        }

    private constructor(parcel: Parcel) : this() {
        restore(parcel)
    }

    override fun describeContents(): Int = 0

    fun readFromParcel(parcel: Parcel) {
        restore(parcel)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(storedException)
    }

    private fun restore(parcel: Parcel) {
        val restored = parcel.readSerializableCompat<Exception>()
        if (restored != null) value = restored
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BridgeFailure> =
            object : Parcelable.Creator<BridgeFailure> {
                override fun createFromParcel(parcel: Parcel) = BridgeFailure(parcel)
                override fun newArray(size: Int): Array<BridgeFailure?> = arrayOfNulls(size)
            }
    }
}
