package com.wisso.wizefiles.feature.internalviewer

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.audioplayer.AudioPlayerIntents
import com.wisso.wizefiles.feature.ebook.EbookViewerIntents
import com.wisso.wizefiles.feature.fontviewer.FontViewerIntents
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewIntents
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewItem
import com.wisso.wizefiles.feature.pdfviewer.PdfViewerIntents
import com.wisso.wizefiles.feature.webdocument.WebDocumentViewerIntents

/** Central router for all WizeFiles-owned file viewing surfaces. */
object InternalOpenIntents {
    fun create(context: Context, current: FileItem, siblings: List<FileItem>): Intent? =
        when (InternalOpenPolicy.targetAfterExtraction(current.mimeType, current.path.name)) {
            InternalOpenPolicy.Target.IMAGE_PREVIEW,
            InternalOpenPolicy.Target.VIDEO_PREVIEW ->
                MediaPreviewIntents.create(context, current, siblings)
            InternalOpenPolicy.Target.AUDIO_PLAYER ->
                AudioPlayerIntents.create(context, current, siblings)
            InternalOpenPolicy.Target.PDF_VIEWER ->
                PdfViewerIntents.create(context, current)
            InternalOpenPolicy.Target.EBOOK_VIEWER ->
                EbookViewerIntents.create(context, current)
            InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER ->
                WebDocumentViewerIntents.create(context, current)
            InternalOpenPolicy.Target.FONT_VIEWER ->
                FontViewerIntents.create(context, current)
            InternalOpenPolicy.Target.CONTAINER_BROWSER,
            InternalOpenPolicy.Target.EXTERNAL_APP -> null
        }

    fun create(
        context: Context,
        current: MediaPreviewItem,
        siblings: List<MediaPreviewItem> = listOf(current)
    ): Intent? = when (InternalOpenPolicy.targetAfterExtraction(current.mimeType, current.path.name)) {
        InternalOpenPolicy.Target.IMAGE_PREVIEW,
        InternalOpenPolicy.Target.VIDEO_PREVIEW ->
            MediaPreviewIntents.create(context, current, siblings)
        InternalOpenPolicy.Target.AUDIO_PLAYER ->
            AudioPlayerIntents.create(context, current, siblings)
        InternalOpenPolicy.Target.PDF_VIEWER ->
            PdfViewerIntents.create(context, current)
        InternalOpenPolicy.Target.EBOOK_VIEWER ->
            EbookViewerIntents.create(context, current)
        InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER ->
            WebDocumentViewerIntents.create(context, current)
        InternalOpenPolicy.Target.FONT_VIEWER ->
            FontViewerIntents.create(context, current)
        InternalOpenPolicy.Target.CONTAINER_BROWSER,
        InternalOpenPolicy.Target.EXTERNAL_APP -> null
    }
}
