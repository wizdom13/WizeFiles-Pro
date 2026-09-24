// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Intent
import android.os.Bundle
import java.nio.file.Path
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.internalviewer.InternalOpenIntents
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewItem
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.startActivitySafe

class OpenFileActivity : BaseThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val path = intent.extraPath
        val mimeType = intent.type?.asMimeTypeOrNull()
        if (path != null && mimeType != null) {
            openFile(path, mimeType)
        }
        finish()
    }

    private fun openFile(path: AppPath, mimeType: MimeType) {
        val legacyPath = path.toLegacyPathOrNull() ?: return
        if (legacyPath.isArchivePath) {
            if (
                InternalOpenPolicy.targetAfterExtraction(mimeType) ==
                InternalOpenPolicy.Target.EXTERNAL_APP
            ) {
                FileOperationService.open(legacyPath, mimeType, false, this)
            } else {
                FileOperationService.openInternalViewer(legacyPath, mimeType, this)
            }
        } else {
            val viewerIntent = InternalOpenIntents.create(
                this,
                MediaPreviewItem(path, mimeType)
            )
            if (viewerIntent != null) {
                startActivitySafe(viewerIntent)
                return
            }
            val intent = legacyPath.fileProviderUri.createViewIntent(mimeType)
                .apply { extraPath = path }
            startActivitySafe(intent)
        }
    }

    companion object {
        private const val ACTION_OPEN_FILE = "com.wisso.wizefiles.intent.action.OPEN_FILE"

        fun createIntent(path: Path, mimeType: MimeType): Intent =
            Intent(ACTION_OPEN_FILE)
                .setPackage(application.packageName)
                .setType(mimeType.value)
                .apply { extraPath = path.toAppPath() }
    }
}
