// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.navigation

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogEditBookmarkDirectoryBinding
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.filebrowser.toUserFriendlyString
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.showImeWithResize
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.ParcelableState
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.getState
import com.wisso.wizefiles.util.launchSafe
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.putState
import com.wisso.wizefiles.util.setTextWithSelection

class EditBookmarkDirectoryDialogFragment : AppCompatDialogFragment() {
    private val openPathLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract(), ::onOpenPathResult)

    private val args by args<Args>()

    private lateinit var path: AppPath

    private lateinit var binding: DialogEditBookmarkDirectoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        path = savedInstanceState?.getState<State>()?.path ?: args.bookmarkDirectory.path
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.navigation_edit_bookmark_directory_title)
            .apply {
                binding = DialogEditBookmarkDirectoryBinding.inflate(context.layoutInflater)
                val bookmarkDirectory = args.bookmarkDirectory
                binding.nameLayout.placeholderText = bookmarkDirectory.defaultName
                if (savedInstanceState == null) {
                    binding.nameEdit.setTextWithSelection(bookmarkDirectory.name)
                }
                updatePathText()
                binding.pathText.setOnClickListener { onEditPath() }
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setNeutralButton(R.string.remove) { _, _ -> remove() }
            .create()
            .apply {
                window!!.showImeWithResize()
            }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(State(path))
    }

    private fun onEditPath() {
        openPathLauncher.launchSafe(path, this)
    }

    private fun onOpenPathResult(result: AppPath?) {
        path = result ?: return
        updatePathText()
    }

    private fun updatePathText() {
        val pathText = path.toLegacyPathOrNull()?.toUserFriendlyString() ?: path.rawPath
        binding.pathText.setText(pathText)
    }

    private fun save() {
        val customName = binding.nameEdit.text.toString()
            .takeIf { it.isNotEmpty() && it != binding.nameLayout.placeholderText }
        val bookmarkDirectory = args.bookmarkDirectory.copy(customName = customName, path = path)
        BookmarkDirectories.replace(bookmarkDirectory)
        finish()
    }

    private fun remove() {
        BookmarkDirectories.remove(args.bookmarkDirectory)
        finish()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    @Parcelize
    class Args(val bookmarkDirectory: BookmarkDirectory) : ParcelableArgs

    @Parcelize
    private class State(var path: AppPath) : ParcelableState
}
