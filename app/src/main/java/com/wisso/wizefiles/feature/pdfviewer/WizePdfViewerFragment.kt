// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.pdfviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy
import java.io.File

/**
 * WizeFiles-owned PDF reader surface.
 *
 * AndroidX PdfViewerFragment proved unreliable on physical devices by remaining indefinitely in
 * its loading state. This fragment deliberately uses the staged regular file directly through the
 * Pdfium-backed AndroidPdfViewer view instead of delegating document loading to AndroidX's
 * sandboxed fragment state machine.
 */
class WizePdfViewerFragment : Fragment() {
    interface Listener {
        fun onPdfDocumentReady(pageCount: Int)
        fun onPdfDocumentError(error: Throwable)
        fun onPdfExternalLink(uri: Uri)
        fun onPdfLinkBlocked(uri: Uri)
        fun onPdfImmersiveModeRequested(enterImmersive: Boolean)
    }

    private var listener: Listener? = null
    private var pdfView: PDFView? = null
    private var pendingFile: File? = null
    private var loadedFile: File? = null

    var documentFile: File?
        get() = pendingFile
        set(value) {
            pendingFile = value
            if (view != null) loadPendingDocument()
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        PDFView(requireContext(), null).also { view ->
            pdfView = view
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPendingDocument()
    }

    fun closeSearch(): Boolean = false

    fun toggleSearch() = Unit

    override fun onDestroyView() {
        pdfView?.recycle()
        pdfView = null
        loadedFile = null
        super.onDestroyView()
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
    }

    private fun loadPendingDocument() {
        val file = pendingFile ?: return
        val view = pdfView ?: return
        if (loadedFile == file) return

        loadedFile = file
        view.recycle()
        view.fromFile(file)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .defaultPage(0)
            .enableAnnotationRendering(true)
            .enableAntialiasing(true)
            .spacing(PAGE_SPACING_DP)
            .autoSpacing(true)
            .pageFitPolicy(FitPolicy.WIDTH)
            .fitEachPage(true)
            .pageSnap(false)
            .pageFling(false)
            .linkHandler { event ->
                val link = event.link
                val destinationPage = link.destPageIdx
                val rawUri = link.uri
                when {
                    destinationPage != null -> view.jumpTo(destinationPage)
                    !rawUri.isNullOrBlank() -> {
                        val uri = Uri.parse(rawUri)
                        if (PdfLinkPolicy.allowsScheme(uri.scheme)) {
                            listener?.onPdfExternalLink(uri)
                        } else {
                            listener?.onPdfLinkBlocked(uri)
                        }
                    }
                }
            }
            .onLoad { pageCount ->
                listener?.onPdfDocumentReady(pageCount)
            }
            .onError { error ->
                loadedFile = null
                listener?.onPdfDocumentError(error)
            }
            .load()
    }

    private companion object {
        const val PAGE_SPACING_DP = 8
    }
}
