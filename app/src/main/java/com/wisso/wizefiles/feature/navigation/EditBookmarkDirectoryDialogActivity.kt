package com.wisso.wizefiles.navigation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.putArgs

class EditBookmarkDirectoryDialogActivity : BaseThemedActivity() {
    private val args by args<EditBookmarkDirectoryDialogFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = EditBookmarkDirectoryDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, EditBookmarkDirectoryDialogFragment::class.java.name)
            }
        }
    }
}
