package com.wisso.wizefiles.vault

import android.app.Dialog
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.databinding.DialogVaultCreateBinding
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.storage.Storages
import com.wisso.wizefiles.storage.VaultStorage
import com.wisso.wizefiles.util.showImeWithResize
import com.wisso.wizefiles.util.applyInsetPadding
import com.wisso.wizefiles.util.finish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddVaultDialogFragment : AppCompatDialogFragment() {
    private lateinit var binding: DialogVaultCreateBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogVaultCreateBinding.inflate(LayoutInflater.from(requireContext()))
        binding.root.applyInsetPadding(applyBottom = true, applyImeBottom = true)
        binding.showPassword.setOnCheckedChangeListener { _, isChecked ->
            val method = if (isChecked) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            binding.password.transformationMethod = method
            binding.confirmPassword.transformationMethod = method
        }
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.navigation_add_vault)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.showImeWithResize()
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener { createVault() }
        }
        return dialog
    }

    private fun createVault() {
        if (!Storages.canAddVault()) {
            ensureProAccess(ProFeature.MULTIPLE_VAULTS)
            return
        }
        val password = binding.password.text?.toString().orEmpty()
        val confirm = binding.confirmPassword.text?.toString().orEmpty()
        when (VaultPasswordValidator.validate(password, confirm)) {
            "empty" -> {
                binding.passwordLayout.error = getString(R.string.vault_error_empty_password)
                return
            }
            "mismatch" -> {
                binding.confirmPasswordLayout.error = getString(R.string.vault_error_password_mismatch)
                return
            }
        }
        binding.passwordLayout.error = null
        binding.confirmPasswordLayout.error = null
        val name = binding.vaultName.text?.toString().orEmpty().ifBlank { defaultVaultName() }
        lifecycleScope.launch {
            val metadata = withContext(Dispatchers.IO) {
                VaultManager(requireContext()).createVault(name, password.toCharArray())
            }
            Storages.addOrReplace(VaultStorage(metadata.vaultId, metadata.name, true))
            startActivity(VaultActivity.createIntent(metadata.vaultId))
            finish()
        }
    }

    private fun defaultVaultName(): String {
        val existing = VaultManager(requireContext()).listVaults().size + 1
        return getString(R.string.vault_name_default_format, existing)
    }
}
