// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import android.content.Context
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R

internal class SyncthingSessionEditor(private val context: Context, initial: SyncthingSessionOptions) {
    val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun number(label: Int, value: Int) = TextInputEditText(context).apply input@ {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value.toString())
        val field = TextInputLayout(context).apply {
            hint = context.getString(label)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(this@input)
        }
        view.addView(field)
    }
    private val manual = number(R.string.syncthing_manual_minutes, initial.manualMinutes)
    private val background = number(R.string.syncthing_background_minutes, initial.backgroundMinutes)
    private val retry = SwitchMaterial(context).apply {
        setText(R.string.syncthing_automatic_retry)
        isChecked = initial.automaticRetry
        view.addView(this)
    }
    private val delay = number(R.string.syncthing_retry_minutes, initial.retryMinutes)
    private val limit = number(R.string.syncthing_retry_limit, initial.retryLimit)

    init {
        view.addView(TextView(context).apply { setText(R.string.syncthing_session_help) })
    }

    fun value() = SyncthingSessionOptions(
        manual.text.toString().toIntOrNull()?.coerceIn(0, 360) ?: 30,
        background.text.toString().toIntOrNull()?.coerceIn(1, 8) ?: 8,
        retry.isChecked,
        delay.text.toString().toIntOrNull()?.coerceIn(1, 300) ?: 5,
        limit.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 6
    )
}
