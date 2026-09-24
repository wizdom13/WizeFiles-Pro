package com.wisso.wizefiles.storage

import android.content.Context
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R
import com.wisso.wizefiles.provider.rclone.RcloneConfigOption
import com.wisso.wizefiles.provider.rclone.RcloneConfigStep
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class RcloneConfigurationQuestionDialog(
    private val context: Context
) {
    suspend fun ask(step: RcloneConfigStep): String {
        val option = step.option
            ?: throw IllegalStateException(
                "rclone returned a configuration state without a question"
            )
        return suspendCancellableCoroutine { continuation ->
            val examples = option.examples
            val message = listOf(step.error, option.help)
                .filter(String::isNotBlank)
                .joinToString("\n\n")
            if (examples.isNotEmpty() && option.exclusive) {
                var selected = examples.indexOfFirst { it.value == option.defaultValue }
                    .takeIf { it >= 0 }
                    ?: 0
                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(humanizeRcloneOptionName(option.name))
                    .setMessage(message)
                    .setSingleChoiceItems(
                        examples.map { it.label }.toTypedArray(),
                        selected
                    ) { _, which -> selected = which }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (continuation.isActive) {
                            continuation.resume(examples[selected].value)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                RcloneConfigurationCancelledException()
                            )
                        }
                    }
                    .create()
                dialog.setOnCancelListener {
                    if (continuation.isActive) {
                        continuation.resumeWithException(RcloneConfigurationCancelledException())
                    }
                }
                continuation.invokeOnCancellation { dialog.dismiss() }
                dialog.show()
            } else {
                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = context.resources.getDimensionPixelSize(R.dimen.screen_edge_margin)
                    setPadding(padding, 0, padding, 0)
                }
                if (message.isNotBlank()) {
                    content.addView(
                        TextView(context).apply {
                            text = message
                            autoLinkMask = android.text.util.Linkify.WEB_URLS
                            movementMethod = LinkMovementMethod.getInstance()
                        }
                    )
                }
                val layout = TextInputLayout(context).apply {
                    hint = humanizeRcloneOptionName(option.name)
                    isErrorEnabled = option.required
                }
                val input = TextInputEditText(context).apply {
                    inputType = rcloneInputTypeFor(option)
                    setText(option.defaultValue)
                }
                if (option.isPassword) {
                    layout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                }
                layout.addView(input)
                content.addView(layout)
                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.rclone_additional_configuration)
                    .setView(content)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                RcloneConfigurationCancelledException()
                            )
                        }
                    }
                    .create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val answer = input.text.toString()
                        if (option.required && answer.isBlank()) {
                            layout.error = context.getString(R.string.rclone_required)
                        } else {
                            dialog.dismiss()
                            if (continuation.isActive) {
                                continuation.resume(answer)
                            }
                        }
                    }
                }
                dialog.setOnCancelListener {
                    if (continuation.isActive) {
                        continuation.resumeWithException(RcloneConfigurationCancelledException())
                    }
                }
                continuation.invokeOnCancellation { dialog.dismiss() }
                dialog.show()
            }
        }
    }
}

internal fun humanizeRcloneOptionName(name: String): String =
    name.replace('_', ' ')
        .replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase() else character.toString()
        }

internal fun rcloneInputTypeFor(option: RcloneConfigOption): Int = when {
    option.isPassword -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    option.type in setOf("int", "int64", "uint", "uint64", "SizeSuffix") ->
        InputType.TYPE_CLASS_NUMBER
    option.name.contains("url", ignoreCase = true) ->
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    else -> InputType.TYPE_CLASS_TEXT
}

internal class RcloneConfigurationCancelledException : Exception()
