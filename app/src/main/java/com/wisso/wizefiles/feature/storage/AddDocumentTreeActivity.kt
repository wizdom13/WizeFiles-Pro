package com.wisso.wizefiles.storage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.getArgsOrNull
import com.wisso.wizefiles.util.putArgs

class AddDocumentTreeActivity : BaseThemedActivity() {
    private val args: Args by lazy {
        intent.extras?.getArgsOrNull(Args::class) ?: Args()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(android.R.id.content, AddDocumentTreeFragment().putArgs(args))
            }
        }
    }

    @Parcelize
    data class Args(
        val providerAuthority: String? = null,
        val providerLabel: String? = null,
        val initialUriString: String? = null
    ) : ParcelableArgs
}
