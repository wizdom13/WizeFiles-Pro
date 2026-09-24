// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.viewer.common.ExternalIntentValidator
import kotlinx.coroutines.launch

class TextEditorActivity : BaseThemedActivity() {
    private var fragment: TextEditorFragment? = null
    private var externalIntentValidated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)

        fragment = supportFragmentManager.findFragmentById(android.R.id.content)
            as? TextEditorFragment
        if (fragment != null) {
            externalIntentValidated = true
            return
        }

        if (intent.action != Intent.ACTION_VIEW) {
            externalIntentValidated = true
            showEditorIfReady()
            return
        }

        if (intent.data == null || intent.extraPath == null) {
            rejectExternalOpen()
            return
        }

        lifecycleScope.launch {
            val validated = ExternalIntentValidator.validate(
                this@TextEditorActivity,
                intent.data,
                ExternalIntentValidator.InputKind.TEXT_VIEW
            )
            if (validated == null) {
                rejectExternalOpen()
                return@launch
            }
            externalIntentValidated = true
            showEditorIfReady()
        }
    }

    override fun onStart() {
        super.onStart()
        showEditorIfReady()
    }

    private fun showEditorIfReady() {
        if (!externalIntentValidated ||
            fragment != null ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            supportFragmentManager.isStateSaved
        ) {
            return
        }

        val editorFragment = TextEditorFragment().putArgs(TextEditorFragment.Args(intent))
        fragment = editorFragment
        supportFragmentManager.commit { add(android.R.id.content, editorFragment) }
    }

    private fun rejectExternalOpen() {
        showToast(R.string.external_intent_open_rejected)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (fragment?.onSupportNavigateUp() == true) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    companion object {
        fun createIntent(path: AppPath): Intent =
            TextEditorActivity::class.createIntent().apply { extraPath = path }
    }
}
