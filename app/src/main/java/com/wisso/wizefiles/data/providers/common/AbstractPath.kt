package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

abstract class AbstractPath<T : AbstractPath<T>> : TypedPath<T> {

    override fun getFileName(): T? =
        nameCount.takeIf { it > 0 }?.let { getName(it - 1) }

    override fun startsWith(other: String): Boolean =
        startsWith(fileSystem.getPath(other))

    override fun endsWith(other: String): Boolean =
        endsWith(fileSystem.getPath(other))

    override fun resolve(other: String): T =
        resolve(fileSystem.getPath(other))

    @Suppress("UNCHECKED_CAST")
    override fun resolveSibling(other: Path): T {
        val currentParent = parent
        return if (currentParent == null) other as T else currentParent.resolve(other)
    }

    override fun resolveSibling(other: String): T =
        resolveSibling(fileSystem.getPath(other))

    override val names: Iterable<T>
        get() = (0 until nameCount).asSequence().map(::getName).asIterable()

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        vararg events: WatchEvent.Kind<*>
    ): WatchKey = register(watcher, events)
}
