// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.provider.archive.ArchiveDisplayNameRegistry
import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.file.Path
import java.nio.file.Paths

/** Keeps launch-route restoration and process-recreation state out of the Fragment lifecycle. */
internal object BrowserStateRestorer {
    data class RestoredState(val path: Path?, val pickOptions: PickOptions?, val unavailable: Boolean)

    @Suppress("DEPRECATION")
    fun restore(
        savedState: Bundle?,
        arguments: Bundle,
        intent: Intent,
        argumentPath: AppPath?,
        stateKey: String,
        downloadsAction: String,
        shouldOpenArchive: (String?, Path, com.wisso.wizefiles.core.files.mime.MimeType?) -> Boolean
    ): RestoredState {
        val externalDisplayName = intent.getStringExtra(EXTRA_EXTERNAL_DISPLAY_NAME)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        var path = savedState?.getParcelable<AppPath>(stateKey)
            ?: arguments.getParcelable<AppPath>(stateKey)
            ?: argumentPath
        when (intent.action) {
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_CREATE_DOCUMENT,
            Intent.ACTION_OPEN_DOCUMENT_TREE -> Unit
            downloadsAction -> path = Paths.get(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
            ).toAppPath()
            else -> path?.toLegacyPathOrNull()?.let { legacyPath ->
                if (shouldOpenArchive(intent.action, legacyPath, intent.type?.asMimeTypeOrNull())) {
                    val archiveRoot = legacyPath.createArchiveRootPath()
                    ArchiveDisplayNameRegistry.remember(
                        archiveRoot,
                        externalDisplayName ?: legacyPath.fileName?.toString()
                    )
                    path = archiveRoot.toAppPath()
                }
            }
        }
        val legacyPath = path?.toLegacyPathOrNull()
        if (legacyPath?.isArchivePath == true) {
            ArchiveDisplayNameRegistry.remember(legacyPath, externalDisplayName)
        }
        val restorationDecision = BrowserNavigationPolicy.restoration(
            BrowserRestorationFacts(
                targetResolved = legacyPath != null,
                targetAvailable = legacyPath != null,
                configuredRootAvailable = false,
                targetWithinConfiguredRoot = false
            )
        )
        return RestoredState(
            path = legacyPath.takeIf {
                restorationDecision == BrowserRestorationDecision.RESTORE_TARGET
            },
            pickOptions = FileListPickerCoordinator.resolveOptions(intent),
            unavailable = path != null && legacyPath == null
        )
    }

    fun save(outState: Bundle, stateKey: String, path: Path?) {
        path?.let { outState.putParcelable(stateKey, it.toAppPath()) }
    }
}
