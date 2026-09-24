// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.root

import android.os.Parcelable
import com.wisso.wizefiles.provider.common.PosixFileStore
import java.nio.file.Path
import java.nio.file.attribute.FileAttributeView

abstract class RootablePosixFileStore(
    private val path: Path,
    private val local: PosixFileStore,
    createRoot: (PosixFileStore) -> RootPosixFileStore
) : PosixFileStore(), Parcelable {
    private val root = createRoot(this)

    final override fun name(): String = local.name()
    final override fun type(): String = local.type()
    final override fun isReadOnly(): Boolean = local.isReadOnly
    final override fun refresh() = local.refresh()

    final override fun setReadOnly(readOnly: Boolean) {
        routed {
            isReadOnly = readOnly
            if (this === root) local.refresh()
        }
    }

    final override fun getTotalSpace(): Long = routed { totalSpace }
    final override fun getUsableSpace(): Long = routed { usableSpace }
    final override fun getUnallocatedSpace(): Long = routed { unallocatedSpace }

    final override fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        local.supportsFileAttributeView(type)

    final override fun supportsFileAttributeView(name: String): Boolean =
        local.supportsFileAttributeView(name)

    private fun <R> routed(action: PosixFileStore.() -> R): R =
        callRootable(path, true, local, root, action)
}
