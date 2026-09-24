// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.asMimeType
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.ParcelableParceler
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.withChooser

class OpenFileAsDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(getString(R.string.file_open_as_title_format, args.path.name))
            .apply {
                val items = FILE_TYPES.map { getString(it.first) }.toTypedArray<CharSequence>()
                setItems(items) { _, which -> openAs(FILE_TYPES[which].second) }
            }
            .create()

    private fun openAs(mimeType: MimeType) {
        val legacyPath = args.path.toLegacyPathOrNull() ?: return
        val intent = legacyPath.fileProviderUri.createViewIntent(mimeType)
            .apply { extraPath = args.path }
            .withChooser()
        startActivitySafe(intent)
        finish()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    companion object {
        private val FILE_TYPES = listOf(
            R.string.file_open_as_type_text to "text/plain",
            R.string.file_open_as_type_image to "image/*",
            R.string.file_open_as_type_audio to "audio/*",
            R.string.file_open_as_type_video to "video/*",
            R.string.file_open_as_type_directory to MimeType.DIRECTORY.value,
            R.string.file_open_as_type_any to "*/*"
        ).map { it.first to it.second.asMimeType() }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> AppPath) : ParcelableArgs
}
