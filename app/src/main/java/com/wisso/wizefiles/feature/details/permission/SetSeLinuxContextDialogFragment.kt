package com.wisso.wizefiles.feature.details.permission

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogSetSelinuxContextBinding
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.showImeWithResize
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.show

class SetSeLinuxContextDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: DialogSetSelinuxContextBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.file_properties_permission_set_selinux_context_title)
            .apply {
                binding = DialogSetSelinuxContextBinding.inflate(context.layoutInflater)
                if (savedInstanceState == null) {
                    binding.seLinuxContextEdit.setText(args.seLinuxContext)
                }
                binding.recursiveCheck.isVisible = args.isDirectory
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> setSeLinuxContext() }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(
                R.string.file_properties_permission_set_selinux_context_restore
            ) { _, _ -> restoreSeLinuxContext() }
            .create()
            .apply {
                window!!.showImeWithResize()
            }

    private fun setSeLinuxContext() {
        val seLinuxContext = binding.seLinuxContextEdit.text.toString()
        val recursive = binding.recursiveCheck.isChecked
        if (!recursive) {
            if (seLinuxContext == args.seLinuxContext) {
                return
            }
        }
        val path = args.file.path.toLegacyPathOrNull() ?: return
        FileOperationService.setSeLinuxContext(
            path, seLinuxContext, recursive, requireContext()
        )
    }

    private fun restoreSeLinuxContext() {
        val recursive = binding.recursiveCheck.isChecked
        val path = args.file.path.toLegacyPathOrNull() ?: return
        FileOperationService.restoreSeLinuxContext(path, recursive, requireContext())
    }

    companion object {
        fun show(file: FileItem, isDirectory: Boolean, seLinuxContext: String, fragment: Fragment) {
            SetSeLinuxContextDialogFragment()
                .putArgs(Args(file, isDirectory, seLinuxContext))
                .show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem, val isDirectory: Boolean, val seLinuxContext: String) :
        ParcelableArgs
}
