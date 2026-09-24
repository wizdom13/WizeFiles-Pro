package com.wisso.wizefiles.feature.fontviewer

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.internalviewer.FontOpenPolicy
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewItem
import com.wisso.wizefiles.util.extraPath

object FontViewerIntents {
    fun create(context: Context, item: FileItem) = create(context, MediaPreviewItem(item.path, item.mimeType, item))
    fun create(context: Context, item: MediaPreviewItem): Intent? {
        if (!FontOpenPolicy.supports(item.path.name, item.mimeType.value)) return null
        return Intent(context, FontViewerActivity::class.java).setType(item.mimeType.value).apply { extraPath = item.path }
    }
}
