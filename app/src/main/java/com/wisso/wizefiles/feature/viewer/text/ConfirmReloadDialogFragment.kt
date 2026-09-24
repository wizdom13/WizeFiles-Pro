// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.show

class ConfirmReloadDialogFragment : AppCompatDialogFragment() {
    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setMessage(R.string.text_editor_reload_message)
            .setPositiveButton(R.string.keep_editing, null)
            .setNegativeButton(R.string.reload) { _, _ -> listener.reload() }
            .create()
    }

    companion object {
        fun show(fragment: Fragment) {
            ConfirmReloadDialogFragment().show(fragment)
        }
    }

    interface Listener {
        fun reload()
    }
}
