// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.navigation

import android.os.Parcelable
import java.nio.file.Path
import kotlin.ConsistentCopyVisibility
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.feature.filebrowser.name
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.takeIfNotEmpty
import java.util.Random

@Parcelize
@ConsistentCopyVisibility
// @see https://youtrack.jetbrains.com/issue/KT-24842
// @Parcelize throws IllegalAccessError if the primary constructor is private.
data class BookmarkDirectory internal constructor(
    val id: Long,
    val customName: String?,
    val path: AppPath
) : Parcelable {
    // We cannot simply use path.hashCode() as ID because different bookmark directories may have
    // the same path.
    constructor(customName: String?, path: AppPath) : this(Random().nextLong(), customName, path)

    // Legacy call sites still produce java.nio.file.Path.
    constructor(customName: String?, path: Path) : this(customName, path.toAppPath())

    val defaultName: String
        get() = path.toLegacyPathOrNull()?.name ?: path.name

    val name: String
        get() = customName?.takeIfNotEmpty() ?: defaultName
}
