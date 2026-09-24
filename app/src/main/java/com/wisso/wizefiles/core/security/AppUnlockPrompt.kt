// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.mainExecutor
import com.wisso.wizefiles.util.showToast

object AppUnlockPrompt {

    fun show(
        activity: FragmentActivity,
        securityManager: AppSecurityManager,
        onUnlocked: () -> Unit,
        onCancelled: () -> Unit
    ) {
        if (securityManager.isBiometricEnabled() && isBiometricAvailable(activity)) {
            showBiometricThenFallback(activity, securityManager, onUnlocked, onCancelled)
        } else {
            showPasswordDialog(activity, securityManager, onUnlocked, onCancelled)
        }
    }

    private fun showBiometricThenFallback(
        activity: FragmentActivity,
        securityManager: AppSecurityManager,
        onUnlocked: () -> Unit,
        onCancelled: () -> Unit
    ) {
        var fallbackShown = false
        val prompt = BiometricPrompt(
            activity,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    securityManager.markUnlocked()
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!fallbackShown) {
                        fallbackShown = true
                        showPasswordDialog(activity, securityManager, onUnlocked, onCancelled)
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.settings_security_biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.settings_security_biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.settings_security_use_password))
            .build()
        runCatching { prompt.authenticate(promptInfo) }
            .onFailure {
                showPasswordDialog(activity, securityManager, onUnlocked, onCancelled)
            }
    }

    private fun showPasswordDialog(
        activity: FragmentActivity,
        securityManager: AppSecurityManager,
        onUnlocked: () -> Unit,
        onCancelled: () -> Unit
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val passwordInput = EditText(activity).apply {
            hint = activity.getString(R.string.settings_security_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val showPassword = CheckBox(activity).apply {
            text = activity.getString(R.string.settings_security_show_password)
            setOnCheckedChangeListener { _, checked ->
                passwordInput.inputType = if (checked) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                passwordInput.setSelection(passwordInput.text.length)
            }
        }
        container.addView(passwordInput)
        container.addView(showPassword)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.settings_security_unlock_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = passwordInput.text?.toString().orEmpty()
            if (!securityManager.verifyPassword(password)) {
                activity.showToast(R.string.settings_security_password_incorrect)
                return@setOnClickListener
            }
            securityManager.markUnlocked()
            dialog.dismiss()
            onUnlocked()
        }
    }

    private fun isBiometricAvailable(activity: FragmentActivity): Boolean {
        val manager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
