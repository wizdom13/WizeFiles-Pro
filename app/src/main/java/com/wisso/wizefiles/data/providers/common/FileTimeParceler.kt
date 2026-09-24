package com.wisso.wizefiles.provider.common

import android.os.Parcel
import java.time.Instant
import kotlinx.parcelize.Parceler
import com.wisso.wizefiles.core.android.compat.readSerializableCompat
import java.nio.file.attribute.FileTime

object FileTimeParceler : Parceler<FileTime?> {
    override fun create(parcel: Parcel): FileTime? =
        parcel.readSerializableCompat<Instant>()?.let { FileTime.from(it) }

    override fun FileTime?.write(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(this?.toInstant())
    }
}
