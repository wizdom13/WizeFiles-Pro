// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.core.android.compat.writeParcelableListCompat
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.util.LinkedMapSet
import com.wisso.wizefiles.util.readParcelableListCompat

class FileItemSet() : LinkedMapSet<AppPath, FileItem>(FileItem::path), Parcelable {
    constructor(parcel: Parcel) : this() {
        addAll(parcel.readParcelableListCompat())
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableListCompat(toList(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FileItemSet> {
        override fun createFromParcel(parcel: Parcel): FileItemSet = FileItemSet(parcel)

        override fun newArray(size: Int): Array<FileItemSet?> = arrayOfNulls(size)
    }
}

fun fileItemSetOf(vararg files: FileItem) = FileItemSet().apply { addAll(files) }
