// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.content.isContentPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.file.Path

internal object BrowserRestorationPolicy {
    fun shouldNavigateUp(currentPath: Path, storageRootPath: Path?): Boolean {
        return BrowserNavigationPolicy.canNavigateUp(
            BrowserNavigationFactsResolver.resolve(currentPath, storageRootPath)
        )
    }
}

/** Resolves concrete provider hierarchy semantics before invoking the pure browser policy. */
internal object BrowserNavigationFactsResolver {
    fun resolve(currentPath: Path, storageRootPath: Path?): BrowserNavigationFacts =
        BrowserNavigationFacts(
            hasParent = currentPath.parent != null,
            configuredRootAvailable = storageRootPath != null,
            isConfiguredRoot = storageRootPath != null && currentPath == storageRootPath,
            isWithinConfiguredRoot = storageRootPath != null && currentPath.startsWith(storageRootPath),
            crossesContainerBoundary = currentPath.isArchivePath &&
                !isExternalArchiveRoot(currentPath)
        )

    fun isExternalArchiveRoot(path: Path): Boolean {
        if (!path.isArchivePath || path.parent != null) {
            return false
        }
        val archiveSource = runCatching { path.archiveFile.toLegacyPathOrNull() }.getOrNull()
        return archiveSource?.isContentPath == true
    }
}

internal object BrowserScrollPolicy {
    fun shouldHideSpeedDial(deltaY: Int, hiddenByScroll: Boolean): Boolean =
        deltaY > 0 && !hiddenByScroll

    fun shouldShowSpeedDial(deltaY: Int, hiddenByScroll: Boolean): Boolean =
        deltaY < 0 && hiddenByScroll
}
