// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.provider.common.PathListDirectoryStream
import com.wisso.wizefiles.util.ParcelSlicedList
import com.wisso.wizefiles.util.readParcelable
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.DirectoryStream
import java.nio.file.Path

class BridgeDirectoryListing private constructor(private val entries: List<Path>) : Parcelable {
    val value: DirectoryStream<Path>
        get() = PathListDirectoryStream(entries) { true }

    @Throws(IOException::class)
    constructor(stream: DirectoryStream<Path>) : this(collectEntries(stream))

    private constructor(parcel: Parcel) : this(readEntries(parcel))

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        if (entries.any { it !is Parcelable }) {
            throw BadParcelableException("Directory entry is not Parcelable")
        }
        @Suppress("UNCHECKED_CAST")
        val parcelables = entries as List<Parcelable>
        parcel.writeParcelable(ParcelSlicedList(parcelables), flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BridgeDirectoryListing> =
            object : Parcelable.Creator<BridgeDirectoryListing> {
                override fun createFromParcel(parcel: Parcel) = BridgeDirectoryListing(parcel)
                override fun newArray(size: Int): Array<BridgeDirectoryListing?> = arrayOfNulls(size)
            }

        @Throws(IOException::class)
        private fun collectEntries(stream: DirectoryStream<Path>): List<Path> {
            val result = ArrayList<Path>()
            try {
                stream.forEach(result::add)
            } catch (failure: DirectoryIteratorException) {
                throw failure.cause ?: IOException("Unable to enumerate remote directory", failure)
            }
            return result
        }

        private fun readEntries(parcel: Parcel): List<Path> {
            val sliced = parcel.readParcelable<ParcelSlicedList<Parcelable>>()
                ?: throw BadParcelableException("Missing directory entries")
            return sliced.list.map { entry ->
                entry as? Path ?: throw BadParcelableException("Directory entry is not a Path")
            }
        }
    }
}
