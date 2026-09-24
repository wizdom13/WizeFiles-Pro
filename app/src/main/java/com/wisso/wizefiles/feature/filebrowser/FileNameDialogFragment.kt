// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.view.LayoutInflater
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.asFileNameOrNull

abstract class FileNameDialogFragment : NameDialogFragment() {
    override val listener: Listener
        get() = super.listener as Listener

    protected open val useCreateEntryDialogStyle: Boolean = false

    override fun onInflateBinding(inflater: LayoutInflater): Binding =
        if (useCreateEntryDialogStyle) {
            inflateNameBinding(inflater, R.layout.dialog_create_entry_name)
        } else {
            super.onInflateBinding(inflater)
        }

    override fun isNameValid(name: String): Boolean {
        if (!super.isNameValid(name)) {
            return false
        }
        if (name.isEmpty()) {
            binding.showNameError(getString(R.string.file_name_error_empty))
            return false
        }
        if (name.asFileNameOrNull() == null) {
            binding.showNameError(getString(R.string.file_name_error_invalid))
            return false
        }
        val listener = listener
        if (listener.hasEntryWithName(name)) {
            binding.showNameError(getString(R.string.file_name_error_already_exists))
            return false
        }
        return true
    }

    interface Listener : NameDialogFragment.Listener {
        fun hasEntryWithName(name: String): Boolean
    }
}
