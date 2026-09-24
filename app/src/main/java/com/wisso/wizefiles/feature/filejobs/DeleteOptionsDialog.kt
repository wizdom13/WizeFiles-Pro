package com.wisso.wizefiles.feature.filejobs

import android.content.Context
import android.os.Parcelable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import kotlinx.parcelize.Parcelize

object DeleteOptionsDialog {
    @Parcelize
    data class UiConfig(
        val permanentDeleteChecked: Boolean? = null,
        val permanentDeleteEnabled: Boolean? = null,
        val permanentDeleteVisible: Boolean? = null,
        val skipConfirmationChecked: Boolean? = null,
        val skipConfirmationEnabled: Boolean? = null,
        val secureShredChecked: Boolean? = null,
        val secureShredEnabled: Boolean? = null,
        val secureShredVisible: Boolean? = null
    ) : Parcelable

    fun create(
        context: Context,
        message: CharSequence,
        supportsSecureShred: Boolean,
        initialOptions: DeleteOptions,
        uiConfig: UiConfig = UiConfig(),
        onConfirm: (DeleteOptions) -> Unit
    ) = run {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_WizeFiles)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.dialog_delete_options, null)
        val messageView = view.findViewById<TextView>(R.id.deleteMessageView)
        val permanentDeleteCheckBox = view.findViewById<CheckBox>(R.id.permanentDeleteCheckBox)
        val skipConfirmationCheckBox =
            view.findViewById<CheckBox>(R.id.skipConfirmationForSessionCheckBox)
        val secureShredCheckBox = view.findViewById<CheckBox>(R.id.secureShredCheckBox)
        val secureShredUnsupportedView = view.findViewById<TextView>(R.id.secureShredUnsupportedView)

        messageView.text = message
        val permanentDeleteVisible = uiConfig.permanentDeleteVisible ?: true
        permanentDeleteCheckBox.visibility =
            if (permanentDeleteVisible) View.VISIBLE else View.GONE
        permanentDeleteCheckBox.isChecked = permanentDeleteVisible && (
            uiConfig.permanentDeleteChecked
                ?: (initialOptions.permanentDelete || initialOptions.secureShred)
            )
        permanentDeleteCheckBox.isEnabled =
            permanentDeleteVisible && (uiConfig.permanentDeleteEnabled ?: true)
        skipConfirmationCheckBox.isChecked =
            uiConfig.skipConfirmationChecked ?: initialOptions.skipConfirmationForSession
        skipConfirmationCheckBox.isEnabled = uiConfig.skipConfirmationEnabled ?: true
        val secureShredVisible = uiConfig.secureShredVisible ?: true
        secureShredCheckBox.visibility = if (secureShredVisible) View.VISIBLE else View.GONE
        secureShredCheckBox.isChecked = secureShredVisible && (
            uiConfig.secureShredChecked ?: (initialOptions.secureShred && supportsSecureShred)
            )
        secureShredCheckBox.isEnabled =
            secureShredVisible && (uiConfig.secureShredEnabled ?: supportsSecureShred)
        secureShredUnsupportedView.visibility =
            if (secureShredVisible && !supportsSecureShred) View.VISIBLE else View.GONE

        fun syncPermanentDeleteState() {
            if (!permanentDeleteVisible) {
                permanentDeleteCheckBox.isChecked = false
                permanentDeleteCheckBox.isEnabled = false
            } else if (secureShredCheckBox.isChecked) {
                permanentDeleteCheckBox.isChecked = true
                permanentDeleteCheckBox.isEnabled = false
            } else {
                permanentDeleteCheckBox.isEnabled = uiConfig.permanentDeleteEnabled ?: true
            }
        }

        secureShredCheckBox.setOnCheckedChangeListener { _, _ -> syncPermanentDeleteState() }
        syncPermanentDeleteState()

        MaterialAlertDialogBuilder(
            themedContext,
            R.style.ThemeOverlay_WizeFiles_FileProperties_Dialog
        )
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onConfirm(
                    DeleteOptions(
                        permanentDelete =
                            permanentDeleteVisible && permanentDeleteCheckBox.isChecked,
                        skipConfirmationForSession = skipConfirmationCheckBox.isChecked,
                        secureShred = secureShredVisible && secureShredCheckBox.isChecked
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    fun show(
        context: Context,
        message: CharSequence,
        supportsSecureShred: Boolean,
        initialOptions: DeleteOptions,
        uiConfig: UiConfig = UiConfig(),
        onConfirm: (DeleteOptions) -> Unit
    ) {
        create(context, message, supportsSecureShred, initialOptions, uiConfig, onConfirm).show()
    }
}
