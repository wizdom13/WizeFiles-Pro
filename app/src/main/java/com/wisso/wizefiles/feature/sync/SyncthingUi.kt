// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SyncthingUi(private val activity: AppCompatActivity, title: String) {
    val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val progress = com.google.android.material.progressindicator.LinearProgressIndicator(activity).apply {
        isIndeterminate = true
        contentDescription = activity.getString(R.string.loading)
        visibility = android.view.View.GONE
    }
    private val buttons = mutableListOf<MaterialButton>()
    var busy = false
        private set
    init {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(activity).apply { this.title = title }
        root.addView(toolbar)
        root.addView(progress)
        root.addView(ScrollView(activity).apply {
            addView(body)
            isFillViewport = true
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        body.setPadding(padding, padding, padding, padding)
        activity.setContentView(root)
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    fun clear() { body.removeAllViews(); buttons.clear() }
    fun text(value: String, heading: Boolean = false) = TextView(activity).apply {
        text = value
        setTextIsSelectable(true)
        setTextAppearance(if (heading) com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        setPadding(0, 16, 0, 8)
        body.addView(this)
    }
    fun button(label: Int, action: () -> Unit) = MaterialButton(activity).apply {
        setText(label)
        isEnabled = !busy
        setOnClickListener { if (!busy) action() }
        body.addView(this)
        buttons.add(this)
    }
    fun <T> action(idle: Boolean = true, block: suspend () -> T, success: (T) -> Unit) {
        if (busy) return
        busy = true
        progress.visibility = android.view.View.VISIBLE
        val enabled = buttons.associateWith { it.isEnabled }
        buttons.forEach { it.isEnabled = false }
        activity.lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    if (idle) SyncthingCoordinator.whileIdle(block) else block()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Toast.makeText(activity, activity.getString(R.string.syncthing_operation_failed,
                    failure.message.orEmpty().take(240)), Toast.LENGTH_LONG).show()
                return@launch
            } finally {
                busy = false
                progress.visibility = android.view.View.GONE
                enabled.forEach { (button, wasEnabled) -> button.isEnabled = wasEnabled }
            }
            success(result)
        }
    }
    fun field(parent: LinearLayout, label: Int, value: String = ""): TextInputEditText {
        val input = TextInputEditText(activity).apply { setText(value) }
        parent.addView(TextInputLayout(activity).apply {
            hint = activity.getString(label)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input)
        })
        return input
    }
}
