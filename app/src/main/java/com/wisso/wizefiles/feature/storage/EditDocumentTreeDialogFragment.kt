package com.wisso.wizefiles.storage

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogEditDocumentTreeBinding
import com.wisso.wizefiles.util.showImeWithResize
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.setTextWithSelection

class EditDocumentTreeDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: DialogEditDocumentTreeBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.storage_edit_document_tree_title)
            .apply {
                binding = DialogEditDocumentTreeBinding.inflate(context.layoutInflater)
                val documentTree = args.documentTree
                binding.nameLayout.placeholderText = documentTree.getDefaultName(
                    binding.nameLayout.context
                )
                if (savedInstanceState == null) {
                    binding.nameEdit.setTextWithSelection(
                        documentTree.getName(binding.nameEdit.context)
                    )
                }
                binding.uriText.setText(documentTree.uri.value.toString())
                val linuxPath = documentTree.linuxPath
                binding.pathLayout.isVisible = linuxPath != null
                binding.pathText.setText(linuxPath)
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setNeutralButton(R.string.remove) { _, _ -> remove() }
            .create()
            .apply {
                window!!.showImeWithResize()
            }

    private fun save() {
        val customName = binding.nameEdit.text.toString()
            .takeIf { it.isNotEmpty() && it != binding.nameLayout.placeholderText }
        val documentTree = args.documentTree.copy(customName = customName)
        Storages.replace(documentTree)
        finish()
    }

    private fun remove() {
        Storages.remove(args.documentTree)
        finish()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    @Parcelize
    class Args(val documentTree: DocumentTree) : ParcelableArgs
}
