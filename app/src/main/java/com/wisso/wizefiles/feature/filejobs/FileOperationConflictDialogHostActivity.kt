// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.putArgs

class FileOperationConflictDialogHostActivity : BaseThemedActivity() {
    private val args by args<FileOperationConflictDialogFragment.Args>()

    private lateinit var fragment: FileOperationConflictDialogFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            fragment = FileOperationConflictDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, FileOperationConflictDialogFragment::class.java.name)
            }
        } else {
            fragment = supportFragmentManager.findFragmentByTag(
                FileOperationConflictDialogFragment::class.java.name
            ) as FileOperationConflictDialogFragment
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            fragment.onFinish()
        }
    }
}
