package com.wisso.wizefiles.feature.filebrowser

import android.os.Bundle
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.createEditIntent
import com.wisso.wizefiles.util.startActivitySafe

// Use a trampoline activity so that we can have a proper icon and title.
class EditFileActivity : BaseThemedActivity() {
    private val args by args<Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = args.path.toLegacyPathOrNull() ?: run {
            finish()
            return
        }
        startActivitySafe(path.fileProviderUri.createEditIntent(args.mimeType))
        finish()
    }

    @Parcelize
    class Args(
        val path: AppPath,
        val mimeType: MimeType
    ) : ParcelableArgs
}
