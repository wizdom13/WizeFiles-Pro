// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * A Path specialization whose navigation operations keep the concrete provider path type.
 */
interface TypedPath<T : TypedPath<T>> : Path {
    val names: Iterable<T>

    override fun getRoot(): T?
    override fun getFileName(): T?
    override fun getParent(): T?
    override fun getName(index: Int): T
    override fun subpath(beginIndex: Int, endIndex: Int): T
    override fun normalize(): T
    override fun resolve(other: Path): T
    override fun resolve(other: String): T
    override fun resolveSibling(other: Path): T
    override fun resolveSibling(other: String): T
    override fun relativize(other: Path): T
    override fun toAbsolutePath(): T

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): T

    override fun iterator(): MutableIterator<Path> =
        throw UnsupportedOperationException(
            "Use the typed names iterable instead of Path.iterator()"
        )
}
