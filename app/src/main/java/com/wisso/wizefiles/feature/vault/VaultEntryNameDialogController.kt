// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.DialogInterface
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.asFileNameOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class VaultEntryNameDialogController(
    private val activity: AppCompatActivity
) {
    fun show(
        @StringRes titleRes: Int,
        initialName: String,
        excludedEntryId: String?,
        entries: List<VaultEntry>,
        currentParentId: String?,
        operation: (String) -> Result<Unit>,
        onSuccess: () -> Unit
    ) {
        val (edit, inputContainer) = createDialogNameInput()
        edit.setText(initialName)
        edit.setSelection(edit.text.length)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setView(inputContainer)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = edit.text.toString().trim()
                if (!validateEntryName(edit, value, entries, currentParentId, excludedEntryId)) {
                    return@setOnClickListener
                }
                activity.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { operation(value) }
                    when {
                        result.isSuccess -> {
                            dialog.dismiss()
                            onSuccess()
                        }
                        result.exceptionOrNull() is VaultEntryNameConflictException -> {
                            edit.error = activity.getString(R.string.file_name_error_already_exists)
                        }
                        else -> {
                            Toast.makeText(activity, R.string.error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun validateEntryName(
        edit: EditText,
        value: String,
        entries: List<VaultEntry>,
        currentParentId: String?,
        excludedEntryId: String?
    ): Boolean {
        val errorRes = when {
            value.isEmpty() -> R.string.file_name_error_empty
            value.asFileNameOrNull() == null -> R.string.file_name_error_invalid
            VaultEntryNamePolicy.hasConflict(
                entries,
                currentParentId,
                value,
                excludedEntryId
            ) -> R.string.file_name_error_already_exists
            else -> null
        }
        if (errorRes != null) {
            edit.error = activity.getString(errorRes)
            edit.requestFocus()
            return false
        }
        edit.error = null
        return true
    }

    private fun createDialogNameInput(): Pair<EditText, FrameLayout> {
        val edit = EditText(activity)
        val horizontalInset = activity.resources.getDimensionPixelSize(R.dimen.dialog_padding)
        val inputContainer = FrameLayout(activity).apply {
            setPadding(horizontalInset, 0, horizontalInset, 0)
            addView(
                edit,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return edit to inputContainer
    }
}
