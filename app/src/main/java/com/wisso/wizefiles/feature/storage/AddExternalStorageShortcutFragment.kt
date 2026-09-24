package com.wisso.wizefiles.storage

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.packageManager
import com.wisso.wizefiles.core.files.uri.ExternalStorageUri
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.createDocumentsUiViewDirectoryIntent
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.showToast

class AddExternalStorageShortcutFragment : Fragment() {
    private val args by args<Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = args.uri
        val hasDocumentsUi = uri.value.createDocumentsUiViewDirectoryIntent()
            .resolveActivity(packageManager) != null
        if (hasDocumentsUi) {
            val externalStorageShortcut = ExternalStorageShortcut(
                null, args.customNameRes?.let { getString(it) }, uri
            )
            Storages.addOrReplace(externalStorageShortcut)
        } else {
            showToast(R.string.activity_not_found)
        }
        finish()
    }

    @Parcelize
    class Args(
        @StringRes val customNameRes: Int?,
        val uri: ExternalStorageUri
    ) : ParcelableArgs
}
