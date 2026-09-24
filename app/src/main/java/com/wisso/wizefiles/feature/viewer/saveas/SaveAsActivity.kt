// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.saveas

import android.os.Bundle
import android.os.Environment
import java.nio.file.Paths
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.util.saveAsPath
import com.wisso.wizefiles.util.showToast

class SaveAsActivity : BaseThemedActivity() {
    private val createFileLauncher =
        registerForActivityResult(FileListActivity.CreateFileContract(), ::onCreateFileResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        AppLog.i("SaveAsActivity", "save-as launched, ${AppLog.summarizeIntent(intent)}")
        val mimeType = intent.type?.asMimeTypeOrNull() ?: MimeType.ANY
        val path = intent.saveAsPath
        if (path == null) {
            AppLog.w("SaveAsActivity", "save-as aborted because source path is missing")
            showToast(R.string.save_as_error)
            finish()
            return
        }
        val title = path.name
        val initialPath =
            Paths.get(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
            ).toAppPath()
        AppLog.i("SaveAsActivity", "Launching create-file with title=$title initialPath=$initialPath")
        createFileLauncher.launch(Triple(mimeType, title, initialPath))
    }

    private fun onCreateFileResult(result: AppPath?) {
        if (result == null) {
            AppLog.i("SaveAsActivity", "save-as canceled by user")
            finish()
            return
        }
        AppLog.i("SaveAsActivity", "save-as destination selected=$result")
        val sourcePath = intent.saveAsPath?.toLegacyPathOrNull()
        val targetPath = result.toLegacyPathOrNull()
        if (sourcePath == null || targetPath == null) {
            AppLog.w("SaveAsActivity", "save-as failed due to non-legacy path source=$sourcePath target=$targetPath")
            showToast(R.string.save_as_error)
            finish()
            return
        }
        FileOperationService.save(sourcePath, targetPath, this)
        finish()
    }
}
