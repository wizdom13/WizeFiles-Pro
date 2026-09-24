package com.wisso.wizefiles.storage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.wisso.wizefiles.core.app.BaseThemedActivity

class AddSafProviderDialogActivity : BaseThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add<AddSafProviderDialogFragment>(AddSafProviderDialogFragment::class.java.name)
            }
        }
    }
}
