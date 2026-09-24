package com.wisso.wizefiles.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.wisso.wizefiles.core.app.BaseThemedActivity

class BookmarkDirectoryListActivity : BaseThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add<BookmarkDirectoryListFragment>(android.R.id.content)
            }
        }
    }
}
