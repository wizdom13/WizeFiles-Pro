package com.wisso.wizefiles.feature.filebrowser

import android.app.Dialog
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogFileDecryptBinding
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.show
import com.wisso.wizefiles.util.ParcelableArgs

class DecryptFilesDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private lateinit var binding: DialogFileDecryptBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogFileDecryptBinding.inflate(LayoutInflater.from(requireContext()))
        binding.showPassword.setOnCheckedChangeListener { _, checked ->
            binding.password.transformationMethod = if (checked) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
        }
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.file_crypto_decrypt)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val password = binding.password.text?.toString().orEmpty()
                if (password.isEmpty()) {
                    binding.passwordLayout.error = getString(R.string.vault_error_empty_password)
                    return@setOnClickListener
                }
                (parentFragment as? Listener)?.decryptFiles(args.files, password.toCharArray())
                dismissAllowingStateLoss()
            }
        }
        return dialog
    }

    companion object {
        fun show(files: FileItemSet, fragment: Fragment) {
            DecryptFilesDialogFragment().putArgs(Args(files)).show(fragment)
        }
    }

    @Parcelize
    data class Args(val files: FileItemSet) : ParcelableArgs

    interface Listener {
        fun decryptFiles(files: FileItemSet, password: CharArray)
    }
}
