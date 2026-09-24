// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.root

import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat
import java.io.IOException
import java.nio.file.Path

interface RootablePath {
    fun isRootRequired(isAttributeAccess: Boolean): Boolean
}

private fun configuredRootStrategy(): RootStrategy {
    if (isRunningAsRoot) return RootStrategy.NEVER
    return runCatching { Settings.ROOT_STRATEGY.valueCompat }
        .getOrElse { RootStrategy.AUTOMATIC }
}

private fun rootRequired(paths: Array<out Path>, attributeAccess: Boolean): Boolean =
    paths.any { path ->
        val rootable = path as? RootablePath
            ?: throw IllegalArgumentException("Path cannot be routed through root: " + path)
        rootable.isRootRequired(attributeAccess)
    }

@Throws(IOException::class)
fun <T, R> callRootable(
    path: Path,
    isAttributeAccess: Boolean,
    localObject: T,
    rootObject: T,
    block: T.() -> R
): R = routeRootable(arrayOf(path), isAttributeAccess, localObject, rootObject, block)

@Throws(IOException::class)
fun <T, R> callRootable(
    path1: Path,
    path2: Path,
    isAttributeAccess: Boolean,
    localObject: T,
    rootObject: T,
    block: T.() -> R
): R = routeRootable(arrayOf(path1, path2), isAttributeAccess, localObject, rootObject, block)

private fun <T, R> routeRootable(
    paths: Array<out Path>,
    attributeAccess: Boolean,
    localObject: T,
    rootObject: T,
    action: T.() -> R
): R {
    val needsRoot = rootRequired(paths, attributeAccess)
    val target = if (configuredRootStrategy().selectsRoot(needsRoot)) rootObject else localObject
    return target.action()
}
