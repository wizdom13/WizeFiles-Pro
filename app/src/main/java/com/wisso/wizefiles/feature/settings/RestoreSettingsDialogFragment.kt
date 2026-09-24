// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import androidx.core.os.BundleCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.util.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun formatRestorePreviewMessage(summary: String, warnings: List<String>): String {
    val warningDetails = warnings.distinct().joinToString("\n") { "• $it" }
    return if (warningDetails.isEmpty()) summary else "$summary\n\n$warningDetails"
}

internal fun createRestoreBackupPickerContract(): FileListActivity.OpenFileContract =
    FileListActivity.OpenFileContract()

internal fun restoreBackupPickerMimeTypes(): List<MimeType> =
    listOf(MimeType.WIZEFILES_BACKUP)

class RestoreSettingsDialogFragment : DialogFragment() {

    private val manager by lazy {
        managerFactoryForTest?.invoke(requireContext()) ?: SettingsBackupRestoreManager(requireContext())
    }
    private var selectedBackup: SelectedBackup? = null
    private lateinit var filePathInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var showPassword: MaterialCheckBox
    private var okButton: Button? = null
    private var selectedBackupEncryptionInfo: SettingsBackupEncryptionInfo? = null
    private var pendingPreview: SettingsRestorePreview? = null

    private val openBackupLauncher = registerForActivityResult(createRestoreBackupPickerContract()) { path ->
        onFileSelected(path)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedBackup = arguments
            ?.let { BundleCompat.getParcelable(it, ARG_INITIAL_URI, Uri::class.java) }
            ?.let { SelectedBackup.ContentUri(it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentView = layoutInflater.inflate(R.layout.dialog_settings_restore, null)
        filePathInput = contentView.findViewById(R.id.restore_file_path_input)
        passwordLayout = contentView.findViewById(R.id.restore_password_layout)
        passwordInput = contentView.findViewById(R.id.restore_password_input)
        showPassword = contentView.findViewById(R.id.restore_show_password)
        filePathInput.hint = getString(R.string.settings_restore_settings_file_hint)
        filePathInput.setSingleLine(true)
        filePathInput.isFocusable = false
        filePathInput.isClickable = false
        selectedBackup?.let { updateSelectedFileLabel(it) }

        fun applyPasswordVisibility(show: Boolean) {
            val method = if (show) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            passwordInput.transformationMethod = method
            passwordInput.setSelection(passwordInput.text?.length ?: 0)
        }

        showPassword.setOnCheckedChangeListener { _, isChecked -> applyPasswordVisibility(isChecked) }
        passwordInput.doAfterTextChanged {
            passwordLayout.error = null
            updateOkButtonState()
        }
        applyPasswordVisibility(false)
        applyEncryptionUi(null)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_restore_settings_title)
            .setView(contentView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.settings_restore_settings_browse, null)
            .create()

        dialog.setOnShowListener {
            okButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            updateOkButtonState()
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener { restoreSettings() }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener { browseFile() }
            selectedBackup?.let { inspectSelectedBackup(it) }
        }
        return dialog
    }

    private fun browseFile() {
        val handledInTest = onBrowseForTest?.invoke() == true
        if (!handledInTest) {
            openBackupLauncher.launch(restoreBackupPickerMimeTypes())
        }
    }

    internal fun onFileSelected(uri: Uri?) {
        uri?.let { selectBackup(SelectedBackup.ContentUri(it)) }
    }

    internal fun onFileSelected(path: AppPath?) {
        path?.let { selectBackup(SelectedBackup.AppFile(it)) }
    }

    private fun selectBackup(backup: SelectedBackup) {
        selectedBackup = backup
        updateSelectedFileLabel(backup)
        inspectSelectedBackup(backup)
    }

    private fun updateSelectedFileLabel(backup: SelectedBackup) {
        filePathInput.setText(
            when (backup) {
                is SelectedBackup.ContentUri -> resolveSelectedFileLabel(backup.uri)
                is SelectedBackup.AppFile -> backup.path.name.ifBlank { backup.path.rawPath }
            }
        )
    }

    private fun resolveSelectedFileLabel(uri: Uri): String {
        val resolvedDisplayName = resolveDisplayNameForTest?.invoke(uri)
            ?: runCatching {
                SettingsBackupViewIntent.resolveDisplayName(requireContext(), uri)
            }.getOrNull()
        return resolvedDisplayName?.takeUnless { it.isBlank() }
            ?: when (uri.scheme) {
                "file" -> uri.lastPathSegment ?: uri.path?.substringAfterLast('/') ?: uri.toString()
                else -> uri.toString()
            }
    }

    private fun inspectSelectedBackup(backup: SelectedBackup) {
        selectedBackupEncryptionInfo = null
        pendingPreview = null
        passwordLayout.error = null
        passwordInput.text?.clear()
        applyEncryptionUi(null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when (backup) {
                        is SelectedBackup.ContentUri ->
                            backupEncryptionInfoForTest?.invoke(backup.uri)
                                ?: manager.inspectBackupEncryption(backup.uri)
                        is SelectedBackup.AppFile -> manager.inspectBackupEncryption(backup.path)
                    }
                }
            }.onSuccess { encryptionInfo ->
                selectedBackupEncryptionInfo = encryptionInfo
                applyEncryptionUi(encryptionInfo)
            }.onFailure {
                selectedBackupEncryptionInfo = null
                applyEncryptionUi(null)
                showToast(getString(R.string.settings_backup_restore_error_generic, it.message.orEmpty()))
            }
        }
    }

    private fun applyEncryptionUi(encryptionInfo: SettingsBackupEncryptionInfo?) {
        val isEncrypted = encryptionInfo?.isEncrypted == true
        val isKnown = encryptionInfo != null
        passwordLayout.isEnabled = isKnown && isEncrypted
        passwordInput.isEnabled = isKnown && isEncrypted
        showPassword.isEnabled = isKnown && isEncrypted
        if (!isEncrypted) {
            passwordInput.text?.clear()
            passwordLayout.error = null
            showPassword.isChecked = false
        }
        updateOkButtonState()
    }

    private fun updateOkButtonState() {
        val hasBackup = selectedBackup != null
        val encryptionInfo = selectedBackupEncryptionInfo
        val enabled = when {
            !hasBackup -> false
            encryptionInfo == null -> false
            encryptionInfo.isEncrypted -> !passwordInput.text.isNullOrBlank()
            else -> true
        }
        okButton?.isEnabled = enabled
    }

    private fun restoreSettings() {
        val backup = selectedBackup ?: return
        val encryptionInfo = selectedBackupEncryptionInfo ?: return
        val password = passwordInput.text?.toString().orEmpty().toCharArray()
        if (encryptionInfo.isEncrypted && (password.isEmpty() || password.all { it.isWhitespace() })) {
            passwordLayout.error = getString(R.string.settings_security_password_required)
            password.fill('\u0000')
            return
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val usablePassword = password.takeUnless { chars ->
                        chars.all { it.isWhitespace() }
                    }
                    when (backup) {
                        is SelectedBackup.ContentUri ->
                            previewForTest?.invoke(backup.uri, usablePassword)
                                ?: manager.previewRestoreFromUri(backup.uri, usablePassword)
                        is SelectedBackup.AppFile ->
                            manager.previewRestoreFromPath(backup.path, usablePassword)
                    }
                }
            }.onSuccess { preview ->
                pendingPreview = preview
                showRestorePreview(preview)
            }.onFailure {
                showToast(getString(R.string.settings_backup_restore_error_generic, it.message.orEmpty()))
            }.also {
                password.fill('\u0000')
            }
        }
    }

    private fun showRestorePreview(preview: SettingsRestorePreview) {
        val summary = getString(
            R.string.settings_restore_preview_message_format,
            preview.schemaVersion,
            preview.settingsToApply.size,
            preview.skippedSettings.size,
            preview.warnings.size
        )
        val message = formatRestorePreviewMessage(summary, preview.warnings)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_restore_preview_title)
            .setMessage(message)
            .setPositiveButton(R.string.settings_restore_preview_apply) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            applyPreviewForTest?.invoke(preview) ?: manager.applyRestorePreview(preview)
                        }
                    }.onSuccess {
                        showToast(R.string.settings_restore_settings_success)
                        dismissAllowingStateLoss()
                    }.onFailure {
                        showToast(getString(R.string.settings_backup_restore_error_generic, it.message.orEmpty()))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        internal var managerFactoryForTest: ((android.content.Context) -> SettingsBackupRestoreManager)? = null
        internal var onBrowseForTest: (() -> Boolean)? = null
        internal var backupEncryptionInfoForTest: ((Uri) -> SettingsBackupEncryptionInfo)? = null
        internal var previewForTest: ((Uri, CharArray?) -> SettingsRestorePreview)? = null
        internal var applyPreviewForTest: ((SettingsRestorePreview) -> Unit)? = null
        internal var resolveDisplayNameForTest: ((Uri) -> String?)? = null

        fun show(fragment: SettingsPreferenceFragment, initialUri: Uri? = null) {
            newInstance(initialUri).show(fragment.parentFragmentManager, TAG)
        }

        private fun newInstance(initialUri: Uri?): RestoreSettingsDialogFragment {
            return RestoreSettingsDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_INITIAL_URI, initialUri)
                }
            }
        }

        private const val TAG = "RestoreSettingsDialogFragment"
        private const val ARG_INITIAL_URI = "initial_uri"
    }

    private sealed interface SelectedBackup {
        data class ContentUri(val uri: Uri) : SelectedBackup
        data class AppFile(val path: AppPath) : SelectedBackup
    }
}
