package com.wisso.wizefiles.storage

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.annotation.DrawableRes
import java.nio.file.Path
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.getDescriptionCompat
import com.wisso.wizefiles.core.android.compat.isPrimaryCompat
import com.wisso.wizefiles.core.android.compat.pathCompat
import com.wisso.wizefiles.core.files.uri.DocumentTreeUri
import com.wisso.wizefiles.core.files.uri.displayName
import com.wisso.wizefiles.core.files.uri.storageVolume
import com.wisso.wizefiles.provider.document.createDocumentTreeRootPath
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.supportsExternalStorageManager
import kotlin.random.Random

@Parcelize
data class DocumentTree(
    override val id: Long,
    override val customName: String?,
    val uri: DocumentTreeUri
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        uri: DocumentTreeUri
    ) : this(id ?: Random.nextLong(), customName, uri)

    override val iconRes: Int
        @DrawableRes
        // Error: Call requires API level 24 (current min is 21):
        // android.os.storage.StorageVolume#equals [NewApi]
        @SuppressLint("NewApi")
        get() =
            // We are using MANAGE_EXTERNAL_STORAGE to access all storage volumes when supported.
            if (!Environment::class.supportsExternalStorageManager()
                && uri.storageVolume.let { it != null && !it.isPrimaryCompat }) {
                R.drawable.ic_sd_card_white_24dp
            } else {
                super.iconRes
            }

    override fun getDefaultName(context: Context): String =
        uri.storageVolume?.getDescriptionCompat(context) ?: uri.displayName
            ?: uri.value.lastPathSegment ?: uri.value.toString()

    override val description: String
        get() = uri.value.toString()

    override val path: Path
        get() = uri.value.createDocumentTreeRootPath()

    override val linuxPath: String?
        get() = uri.storageVolume?.pathCompat

    override fun createEditIntent(): Intent =
        EditDocumentTreeDialogActivity::class.createIntent()
            .putArgs(EditDocumentTreeDialogFragment.Args(this))
}
