package com.wisso.wizefiles.vault

import android.app.Dialog
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogVaultUnlockBinding
import com.wisso.wizefiles.util.showImeWithResize
import com.wisso.wizefiles.util.applyInsetPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnlockVaultDialogFragment : AppCompatDialogFragment() {
    private lateinit var binding: DialogVaultUnlockBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogVaultUnlockBinding.inflate(LayoutInflater.from(requireContext()))
        binding.root.applyInsetPadding(applyBottom = true, applyImeBottom = true)
        val vaultId = requireArguments().getString(ARG_VAULT_ID)!!
        val manager = VaultManager(requireContext())
        val metadata = manager.listVaults().first { it.vaultId == vaultId }
        binding.showPassword.setOnCheckedChangeListener { _, isChecked ->
            binding.password.transformationMethod = if (isChecked) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
        }
        binding.biometricButton.isEnabled = metadata.biometricEnabled && isBiometricAvailable()
        binding.biometricButton.setOnClickListener { startBiometricUnlock(metadata) }
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(getString(R.string.vault_unlock_title, metadata.name))
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vault_unlock_action, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.showImeWithResize()
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                unlockWithPassword(vaultId)
            }
        }
        return dialog
    }

    private fun unlockWithPassword(vaultId: String) {
        val passwordText = binding.password.text?.toString().orEmpty()
        if (passwordText.isEmpty()) {
            binding.passwordLayout.error = getString(R.string.vault_error_empty_password)
            return
        }
        binding.passwordLayout.error = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                VaultManager(requireContext()).unlockWithPassword(vaultId, passwordText.toCharArray())
            }
            if (result.isSuccess) {
                setFragmentResult(REQUEST_KEY, Bundle().apply { putString(KEY_VAULT_ID, vaultId) })
                dismissAllowingStateLoss()
            } else {
                binding.passwordLayout.error = getString(R.string.vault_error_unlock_failed)
            }
        }
    }

    private fun startBiometricUnlock(metadata: VaultMetadata) {
        val encrypted = metadata.biometricWrappedVmkBase64 ?: return
        val iv = metadata.biometricIvBase64 ?: return
        if (!metadata.biometricEnabled || !isBiometricAvailable()) {
            binding.passwordLayout.error = getString(R.string.vault_error_biometric_unavailable)
            return
        }
        val cipher = runCatching {
            VaultKeystore.decryptCipher(metadata.biometricKeyAlias, VaultCrypto.fromBase64(iv))
        }.getOrElse {
            binding.passwordLayout.error = getString(R.string.vault_error_biometric_unavailable)
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(requireContext()),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val crypto = result.cryptoObject?.cipher ?: return
                    runCatching {
                        val vmk = crypto.doFinal(VaultCrypto.fromBase64(encrypted))
                        VaultSessionManager.putUnlockedKey(metadata.vaultId, vmk)
                    }.onSuccess {
                        setFragmentResult(
                            REQUEST_KEY,
                            Bundle().apply { putString(KEY_VAULT_ID, metadata.vaultId) }
                        )
                        dismissAllowingStateLoss()
                    }.onFailure {
                        binding.passwordLayout.error = getString(R.string.vault_error_unlock_failed)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE
                    ) {
                        binding.passwordLayout.error = getString(R.string.vault_error_biometric_unavailable)
                    }
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.vault_biometric_title))
                .setSubtitle(getString(R.string.vault_biometric_subtitle))
                .setNegativeButtonText(getString(android.R.string.cancel))
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(requireContext())
        val result = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    companion object {
        const val REQUEST_KEY = "unlock_vault"
        const val KEY_VAULT_ID = "vault_id"
        private const val ARG_VAULT_ID = "arg_vault_id"

        fun newInstance(vaultId: String) = UnlockVaultDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_VAULT_ID, vaultId) }
        }
    }
}
