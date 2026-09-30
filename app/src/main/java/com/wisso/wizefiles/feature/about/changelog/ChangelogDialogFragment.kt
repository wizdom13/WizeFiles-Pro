// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about.changelog

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R

class ChangelogDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.changelog_whats_new, requireArguments().getString(VERSION)))
            .setView(changelogTextView(requireContext(), requireArguments().getString(HTML).orEmpty(), true))
            .setNegativeButton(R.string.close) { _, _ -> acknowledge() }
            .setPositiveButton(R.string.changelog_view_full) { _, _ ->
                acknowledge()
                startActivity(Intent(requireContext(), ChangelogActivity::class.java))
            }
            .create()

    override fun onCancel(dialog: DialogInterface) {
        acknowledge()
        super.onCancel(dialog)
    }

    private fun acknowledge() {
        ChangelogPreferences(requireContext()).acknowledge(requireArguments().getInt(ORDER))
    }

    companion object {
        const val TAG = "changelog-notice"
        private const val VERSION = "version"
        private const val ORDER = "order"
        private const val HTML = "html"

        internal fun newInstance(notice: ChangelogNotice) = ChangelogDialogFragment().apply {
            arguments = bundleOf(VERSION to notice.version, ORDER to notice.order, HTML to notice.html)
        }
    }
}
