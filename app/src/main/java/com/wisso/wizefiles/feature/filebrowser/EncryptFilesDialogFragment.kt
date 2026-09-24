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
import com.wisso.wizefiles.databinding.DialogFileEncryptBinding
import com.wisso.wizefiles.feature.filejobs.FileEncryptionAlgorithm
import com.wisso.wizefiles.feature.filejobs.FileKdfAlgorithm
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.show
import com.wisso.wizefiles.util.ParcelableArgs

class EncryptFilesDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private lateinit var binding: DialogFileEncryptBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogFileEncryptBinding.inflate(LayoutInflater.from(requireContext()))
        binding.showPassword.setOnCheckedChangeListener { _, checked ->
            val method = if (checked) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            binding.password.transformationMethod = method
            binding.confirmPassword.transformationMethod = method
        }
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.file_crypto_encrypt)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val password = binding.password.text?.toString().orEmpty()
                val confirm = binding.confirmPassword.text?.toString().orEmpty()
                if (password.isEmpty()) {
                    binding.passwordLayout.error = getString(R.string.vault_error_empty_password)
                    return@setOnClickListener
                }
                if (password != confirm) {
                    binding.confirmPasswordLayout.error = getString(R.string.vault_error_password_mismatch)
                    return@setOnClickListener
                }
                val algorithm = if (binding.encryptionAlgorithmGroup.checkedRadioButtonId == R.id.chacha) {
                    FileEncryptionAlgorithm.CHACHA20_POLY1305
                } else {
                    FileEncryptionAlgorithm.AES_256_GCM
                }
                val kdf = if (binding.kdfGroup.checkedRadioButtonId == R.id.bcrypt) {
                    FileKdfAlgorithm.BCRYPT
                } else {
                    FileKdfAlgorithm.ARGON2ID
                }
                (parentFragment as? Listener)?.encryptFiles(args.files, password.toCharArray(), algorithm.id, kdf.id)
                dismissAllowingStateLoss()
            }
        }
        return dialog
    }

    companion object {
        fun show(files: FileItemSet, fragment: Fragment) {
            EncryptFilesDialogFragment().putArgs(Args(files)).show(fragment)
        }
    }

    @Parcelize
    data class Args(val files: FileItemSet) : ParcelableArgs

    interface Listener {
        fun encryptFiles(files: FileItemSet, password: CharArray, algorithmId: Int, kdfId: Int)
    }
}
