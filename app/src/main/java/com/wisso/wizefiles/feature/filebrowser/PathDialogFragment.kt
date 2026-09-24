// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.net.Uri
import com.wisso.wizefiles.R
import com.wisso.wizefiles.storage.createOrLog
import com.wisso.wizefiles.util.asPathNameOrNull
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

abstract class PathDialogFragment : NameDialogFragment() {
    override fun isNameValid(name: String): Boolean {
        if (!super.isNameValid(name)) {
            return false
        }
        if (name.isEmpty()) {
            binding.showNameError(getString(R.string.file_list_path_error_empty))
            return false
        }
        if (name.asPathNameOrNull() == null || name.toPathOrNull() == null) {
            binding.showNameError(getString(R.string.file_list_path_error_invalid))
            return false
        }
        return true
    }

    final override fun onOk(name: String) {
        onOk(name.toPathOrNull()!!)
    }

    private fun String.toPathOrNull(): Path? {
        val uri = URI::class.createOrLog(toString())
            // Also try to accept decoded path.
            ?: Uri.parse(this).let {
                URI::class.createOrLog(
                    it.scheme, it.userInfo, it.host, it.port, it.path, it.query, it.fragment
                )
            }
        if (uri != null) {
            try {
                return Paths.get(uri)
            } catch (e: Exception) {
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            }
        }
        if (startsWith('/')) {
            return Paths.get(this)
        }
        return null
    }

    abstract fun onOk(path: Path)
}
