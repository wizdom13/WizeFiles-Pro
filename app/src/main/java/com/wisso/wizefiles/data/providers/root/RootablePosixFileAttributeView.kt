// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.root

import android.os.Parcelable
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.PosixFileAttributeView
import com.wisso.wizefiles.provider.common.PosixFileAttributes
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import java.nio.file.Path
import java.nio.file.attribute.FileTime

abstract class RootablePosixFileAttributeView(
    private val path: Path,
    private val local: PosixFileAttributeView,
    createRoot: (PosixFileAttributeView) -> RootPosixFileAttributeView
) : PosixFileAttributeView, Parcelable {
    private val root = createRoot(this)

    final override fun name(): String = local.name()
    final override fun readAttributes(): PosixFileAttributes = routed { readAttributes() }

    final override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) = routed { setTimes(lastModifiedTime, lastAccessTime, createTime) }

    final override fun setOwner(owner: PosixUser) = routed { setOwner(owner) }
    final override fun setGroup(group: PosixGroup) = routed { setGroup(group) }
    final override fun setMode(mode: Set<PosixFileModeBit>) = routed { setMode(mode) }
    final override fun setSeLinuxContext(context: ByteString) =
        routed { setSeLinuxContext(context) }
    final override fun restoreSeLinuxContext() = routed { restoreSeLinuxContext() }

    private fun <R> routed(action: PosixFileAttributeView.() -> R): R =
        callRootable(path, true, local, root, action)
}
