// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.DocumentsContractCompat
import com.wisso.wizefiles.databinding.DialogEditExternalStorageShortcutBinding
import com.wisso.wizefiles.core.files.uri.ExternalStorageUri
import com.wisso.wizefiles.core.files.uri.displayName
import com.wisso.wizefiles.util.showImeWithResize
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.hideTextInputLayoutErrorOnTextChange
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.setTextWithSelection
import com.wisso.wizefiles.util.takeIfNotEmpty

class EditExternalStorageShortcutDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: DialogEditExternalStorageShortcutBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.storage_edit_external_storage_shortcut_title)
            .apply {
                binding = DialogEditExternalStorageShortcutBinding.inflate(context.layoutInflater)
                val externalStorageShortcut = args.externalStorageShortcut
                binding.rootIdEdit.hideTextInputLayoutErrorOnTextChange(binding.rootIdLayout)
                binding.rootIdEdit.doAfterTextChanged { updateNamePlaceholder() }
                binding.pathEdit.doAfterTextChanged { updateNamePlaceholder() }
                if (savedInstanceState == null) {
                    binding.nameEdit.setTextWithSelection(
                        externalStorageShortcut.getName(binding.nameEdit.context)
                    )
                    val uri = externalStorageShortcut.uri
                    binding.rootIdEdit.setText(uri.rootId)
                    binding.pathEdit.setText(uri.path)
                }
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setNeutralButton(R.string.remove) { _, _ -> remove() }
            .create()
            .apply {
                window!!.showImeWithResize()
                // Override the listener here so that we have control over when to close the dialog.
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
                }
            }

    private fun updateNamePlaceholder() {
        val rootId = binding.rootIdEdit.text.toString().takeIfNotEmpty()
            ?: DocumentsContractCompat.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID
        val path = binding.pathEdit.text.toString().dropWhile { it == '/' }
        binding.nameLayout.placeholderText = ExternalStorageUri(rootId, path).displayName
    }

    private fun save() {
        val externalStorageShortcut = getExternalStorageShortcutOrSetError() ?: return
        Storages.replace(externalStorageShortcut)
        finish()
    }

    private fun getExternalStorageShortcutOrSetError(): ExternalStorageShortcut? {
        var errorEdit: TextInputEditText? = null
        val customName = binding.nameEdit.text.toString()
            .takeIf { it.isNotEmpty() && it != binding.nameLayout.placeholderText }
        val rootId = binding.rootIdEdit.text.toString().takeIfNotEmpty()
        if (rootId == null) {
            binding.rootIdLayout.error =
                getString(R.string.storage_edit_external_storage_shortcut_root_id_error_empty)
            if (errorEdit == null) {
                errorEdit = binding.rootIdEdit
            }
        }
        val path = binding.pathEdit.text.toString().dropWhile { it == '/' }
        val uri = rootId?.let { ExternalStorageUri(rootId, path) }
        if (errorEdit != null) {
            errorEdit.requestFocus()
            return null
        }
        return ExternalStorageShortcut(args.externalStorageShortcut.id, customName, uri!!)
    }

    private fun remove() {
        Storages.remove(args.externalStorageShortcut)
        finish()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    @Parcelize
    class Args(val externalStorageShortcut: ExternalStorageShortcut) : ParcelableArgs
}
