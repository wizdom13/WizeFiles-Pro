// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.commit
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs

class EditSmbServerActivity : BaseThemedActivity() {
    private val args by args<EditSmbServerFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = EditSmbServerFragment().putArgs(args)
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        }
    }

    class Contract : ActivityResultContract<EditSmbServerFragment.Args, Boolean>() {
        override fun createIntent(context: Context, input: EditSmbServerFragment.Args): Intent =
            EditSmbServerActivity::class.createIntent().putArgs(input)

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            resultCode == Activity.RESULT_OK
    }
}
