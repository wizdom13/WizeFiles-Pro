package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.core.android.compat.readSerializableCompat
import com.wisso.wizefiles.util.readParcelable
import java.io.Serializable
import java.nio.file.CopyOption
import java.nio.file.LinkOption

class BridgeCopyOptions(options: Array<out CopyOption>) : Parcelable {
    val value: Array<out CopyOption> = options.copyOf()

    private constructor(parcel: Parcel) : this(readOptions(parcel))

    override fun describeContents(): Int =
        value.filterIsInstance<Parcelable>().fold(0) { mask, option ->
            mask or option.describeContents()
        }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(value.size)
        value.forEach { option ->
            when (option) {
                is Parcelable -> {
                    parcel.writeInt(PARCELABLE_OPTION)
                    parcel.writeParcelable(option, flags)
                }
                is Serializable -> {
                    parcel.writeInt(SERIALIZABLE_OPTION)
                    parcel.writeSerializable(option)
                }
                else -> throw UnsupportedOperationException(
                    "Copy option cannot cross the process boundary: " + option
                )
            }
        }
    }

    companion object {
        private const val PARCELABLE_OPTION = 1
        private const val SERIALIZABLE_OPTION = 2
        private const val MAX_OPTION_COUNT = 128

        @JvmField
        val CREATOR: Parcelable.Creator<BridgeCopyOptions> =
            object : Parcelable.Creator<BridgeCopyOptions> {
                override fun createFromParcel(parcel: Parcel) = BridgeCopyOptions(parcel)
                override fun newArray(size: Int): Array<BridgeCopyOptions?> = arrayOfNulls(size)
            }

        private fun readOptions(parcel: Parcel): Array<out CopyOption> {
            val count = parcel.readInt()
            if (count !in 0..MAX_OPTION_COUNT) {
                throw BadParcelableException("Invalid copy option count: " + count)
            }
            return Array(count) {
                when (val encoding = parcel.readInt()) {
                    PARCELABLE_OPTION ->
                        parcel.readParcelable<Parcelable>() as? CopyOption
                            ?: throw BadParcelableException("Invalid Parcelable copy option")
                    SERIALIZABLE_OPTION ->
                        parcel.readSerializableCompat<Serializable>() as? CopyOption
                            ?: throw BadParcelableException("Invalid Serializable copy option")
                    else -> throw BadParcelableException(
                        "Unknown copy option encoding: " + encoding
                    )
                }
            }
        }
    }
}

fun Array<out CopyOption>.toParcelable(): BridgeCopyOptions = BridgeCopyOptions(this)

fun Array<out LinkOption>.toParcelable(): BridgeSerializable =
    (this as Serializable).toParcelable()
