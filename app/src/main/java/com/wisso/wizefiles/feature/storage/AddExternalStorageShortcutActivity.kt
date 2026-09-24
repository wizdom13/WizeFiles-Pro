package com.wisso.wizefiles.storage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.putArgs

class AddExternalStorageShortcutActivity : BaseThemedActivity() {
    private val args by args<AddExternalStorageShortcutFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = AddExternalStorageShortcutFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, AddExternalStorageShortcutFragment::class.java.name)
            }
        }
    }
}
