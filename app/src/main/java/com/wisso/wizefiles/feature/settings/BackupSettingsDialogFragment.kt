// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.app.Dialog
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.util.createSendStreamIntent
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.withChooser
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupSettingsDialogFragment : DialogFragment() {

    private val manager by lazy {
        managerFactoryForTest?.invoke(requireContext()) ?: SettingsBackupRestoreManager(requireContext())
    }
    private var positiveButton: Button? = null
    private var neutralButton: Button? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentView = layoutInflater.inflate(R.layout.dialog_settings_backup, null)
        val fileNameInput = contentView.findViewById<EditText>(R.id.backup_file_name_input).apply {
            setText(manager.defaultBackupFileName())
            setSingleLine(true)
        }
        val encryptBackup = contentView.findViewById<MaterialCheckBox>(R.id.backup_encrypt_backup).apply {
            isChecked = true
        }
        val passwordLayout = contentView.findViewById<TextInputLayout>(R.id.backup_password_layout)
        val confirmPasswordLayout = contentView.findViewById<TextInputLayout>(R.id.backup_confirm_password_layout)
        val passwordInput = contentView.findViewById<EditText>(R.id.backup_password_input)
        val confirmPasswordInput = contentView.findViewById<EditText>(R.id.backup_confirm_password_input)
        val showPassword = contentView.findViewById<MaterialCheckBox>(R.id.backup_show_password)

        fun applyPasswordVisibility(show: Boolean) {
            val method = if (show) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            passwordInput.transformationMethod = method
            confirmPasswordInput.transformationMethod = method
            passwordInput.setSelection(passwordInput.text?.length ?: 0)
            confirmPasswordInput.setSelection(confirmPasswordInput.text?.length ?: 0)
        }

        fun updateEncryptionUi() {
            val enabled = encryptBackup.isChecked
            passwordLayout.isEnabled = enabled
            confirmPasswordLayout.isEnabled = enabled
            passwordInput.isEnabled = enabled
            confirmPasswordInput.isEnabled = enabled
            showPassword.isEnabled = enabled
            if (!enabled) {
                passwordLayout.error = null
                confirmPasswordLayout.error = null
                passwordInput.text?.clear()
                confirmPasswordInput.text?.clear()
                showPassword.isChecked = false
            }
            applyPasswordVisibility(showPassword.isChecked)
        }

        encryptBackup.setOnCheckedChangeListener { _, _ -> updateEncryptionUi() }
        showPassword.setOnCheckedChangeListener { _, isChecked -> applyPasswordVisibility(isChecked) }
        passwordInput.doAfterTextChanged {
            passwordLayout.error = null
            confirmPasswordLayout.error = null
        }
        confirmPasswordInput.doAfterTextChanged { confirmPasswordLayout.error = null }
        updateEncryptionUi()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_backup_settings_title)
            .setView(contentView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.share, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    neutralButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                    positiveButton?.setOnClickListener {
                        backup(
                            fileName = fileNameInput.text.toString(),
                            encrypt = encryptBackup.isChecked,
                            password = passwordInput.text?.toString().orEmpty().toCharArray(),
                            confirmPassword = confirmPasswordInput.text?.toString().orEmpty().toCharArray(),
                            passwordLayout = passwordLayout,
                            confirmPasswordLayout = confirmPasswordLayout,
                            share = false
                        )
                    }
                    neutralButton?.setOnClickListener {
                        backup(
                            fileName = fileNameInput.text.toString(),
                            encrypt = encryptBackup.isChecked,
                            password = passwordInput.text?.toString().orEmpty().toCharArray(),
                            confirmPassword = confirmPasswordInput.text?.toString().orEmpty().toCharArray(),
                            passwordLayout = passwordLayout,
                            confirmPasswordLayout = confirmPasswordLayout,
                            share = true
                        )
                    }
                }
            }
    }

    private fun backup(
        fileName: String,
        encrypt: Boolean,
        password: CharArray,
        confirmPassword: CharArray,
        passwordLayout: TextInputLayout,
        confirmPasswordLayout: TextInputLayout,
        share: Boolean
    ) {
        if (encrypt) {
            if (password.isEmpty() || password.all { it.isWhitespace() }) {
                passwordLayout.error = getString(R.string.settings_security_password_required)
                return
            }
            if (!password.contentEquals(confirmPassword)) {
                confirmPasswordLayout.error = getString(R.string.settings_security_password_mismatch)
                return
            }
        }
        positiveButton?.isEnabled = false
        neutralButton?.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    backupActionForTest?.invoke(fileName, encrypt, password.takeUnless { it.all { ch -> ch.isWhitespace() } })
                        ?: manager.backupToDownloads(
                            fileName = fileName,
                            encrypt = encrypt,
                            password = password.takeUnless { it.all { ch -> ch.isWhitespace() } }
                        )
                }
            }.onSuccess { backupFile ->
                if (share) {
                    val path = Path.of(backupFile.absolutePath)
                    val shareIntent = listOf(path.fileProviderUri)
                        .createSendStreamIntent(listOf(MimeType.WIZEFILES_BACKUP))
                        .withChooser()
                    startActivitySafe(shareIntent)
                } else {
                    showToast(getString(R.string.settings_backup_saved_to_downloads, backupFile.name))
                }
                dismissAllowingStateLoss()
            }.onFailure {
                positiveButton?.isEnabled = true
                neutralButton?.isEnabled = true
                showToast(getString(R.string.settings_backup_restore_error_generic, it.message.orEmpty()))
            }.also {
                password.fill('\u0000')
                confirmPassword.fill('\u0000')
            }
        }
    }

    companion object {
        internal var managerFactoryForTest: ((android.content.Context) -> SettingsBackupRestoreManager)? = null
        internal var backupActionForTest: ((String, Boolean, CharArray?) -> java.io.File)? = null

        fun show(fragment: SettingsPreferenceFragment) {
            BackupSettingsDialogFragment().show(fragment.parentFragmentManager, TAG)
        }

        private const val TAG = "BackupSettingsDialogFragment"
    }
}
