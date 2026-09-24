package com.wisso.wizefiles.storage

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.startActivitySafe

class AddSafProviderDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.storage_add_document_tree_title)
            .setMessage(R.string.storage_saf_instruction_sheet_message)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(R.string.storage_saf_instruction_sheet_open_picker) { _, _ ->
                startActivitySafe(
                    AddDocumentTreeActivity::class.createIntent().putArgs(
                        AddDocumentTreeActivity.Args()
                    )
                )
                finish()
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        finish()
    }
}
