package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.core.android.compat.readSerializableCompat
import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.toAttribute
import java.io.Serializable
import java.nio.file.attribute.FileAttribute

class BridgeFileAttributes(attributes: Array<out FileAttribute<*>>) : Parcelable {
    val value: Array<out FileAttribute<*>> = attributes.copyOf()

    private constructor(parcel: Parcel) : this(readAttributes(parcel))

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        val encodedModes = value.map { attribute ->
            PosixFileMode.fromAttribute(attribute).toSet()
        }
        parcel.writeSerializable(ArrayList(encodedModes))
    }

    companion object {
        private const val MAX_ATTRIBUTE_COUNT = 128

        @JvmField
        val CREATOR: Parcelable.Creator<BridgeFileAttributes> =
            object : Parcelable.Creator<BridgeFileAttributes> {
                override fun createFromParcel(parcel: Parcel) = BridgeFileAttributes(parcel)
                override fun newArray(size: Int): Array<BridgeFileAttributes?> = arrayOfNulls(size)
            }

        private fun readAttributes(parcel: Parcel): Array<out FileAttribute<*>> {
            val encoded = parcel.readSerializableCompat<Serializable>() as? List<*>
                ?: throw BadParcelableException("Missing file attribute list")
            if (encoded.size > MAX_ATTRIBUTE_COUNT) {
                throw BadParcelableException("Too many file attributes: " + encoded.size)
            }
            return encoded.map { item ->
                val values = item as? Set<*>
                    ?: throw BadParcelableException("Invalid file attribute payload")
                values.map { value ->
                    value as? PosixFileModeBit
                        ?: throw BadParcelableException("Invalid POSIX mode value")
                }.toSet().toAttribute()
            }.toTypedArray()
        }
    }
}

fun Array<out FileAttribute<*>>.toParcelable(): BridgeFileAttributes =
    BridgeFileAttributes(this)
