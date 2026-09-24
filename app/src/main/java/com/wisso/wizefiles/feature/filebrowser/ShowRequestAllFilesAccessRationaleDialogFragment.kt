// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.show

class ShowRequestAllFilesAccessRationaleDialogFragment : AppCompatDialogFragment() {
    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setMessage(R.string.all_files_access_rationale_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                listener.onShowRequestAllFilesAccessRationaleResult(true)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                listener.onShowRequestAllFilesAccessRationaleResult(false)
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        listener.onShowRequestAllFilesAccessRationaleResult(false)
    }

    companion object {
        fun show(fragment: Fragment) {
            ShowRequestAllFilesAccessRationaleDialogFragment().show(fragment)
        }
    }

    interface Listener {
        fun onShowRequestAllFilesAccessRationaleResult(shouldRequest: Boolean)
    }
}
