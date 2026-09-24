package com.wisso.wizefiles.feature.pdfviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.content.ExternalLink
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment
import kotlin.math.roundToInt

class WizePdfViewerFragment : PdfViewerFragment() {
    interface Listener {
        fun onPdfDocumentReady(pageCount: Int)
        fun onPdfDocumentError(error: Throwable)
        fun onPdfExternalLink(uri: Uri)
        fun onPdfLinkBlocked(uri: Uri)
        fun onPdfImmersiveModeRequested(enterImmersive: Boolean)
    }

    private var listener: Listener? = null
    private var readerView: PdfView? = null
    private val pageLayoutListener = View.OnLayoutChangeListener {
            _, _, _, _, _, _, _, _, _ ->
        updatePageLayout()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isToolboxVisible = false
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        isToolboxVisible = false
        listener?.onPdfDocumentReady(document.pageCount)
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        listener?.onPdfDocumentError(error)
    }

    override fun onLinkClicked(externalLink: ExternalLink): Boolean {
        val uri = externalLink.uri
        if (PdfLinkPolicy.allowsScheme(uri.scheme)) {
            listener?.onPdfExternalLink(uri)
        } else {
            listener?.onPdfLinkBlocked(uri)
        }
        return true
    }

    override fun onRequestImmersiveMode(enterImmersive: Boolean) {
        listener?.onPdfImmersiveModeRequested(enterImmersive)
    }

    @OptIn(ExperimentalPdfApi::class)
    override fun onPdfViewCreated(pdfView: PdfView) {
        readerView?.removeOnLayoutChangeListener(pageLayoutListener)
        readerView = pdfView
        pdfView.addOnLayoutChangeListener(pageLayoutListener)
        pdfView.post {
            updatePageLayout()
            pdfView.pdfDocument?.let { listener?.onPdfDocumentReady(it.pageCount) }
        }
    }

    fun closeSearch(): Boolean {
        if (!isTextSearchActive) return false
        isTextSearchActive = false
        return true
    }

    fun toggleSearch() {
        isTextSearchActive = !isTextSearchActive
    }

    override fun onDestroyView() {
        readerView?.removeOnLayoutChangeListener(pageLayoutListener)
        readerView = null
        super.onDestroyView()
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
    }

    private fun updatePageLayout() {
        val view = readerView ?: return
        if (view.width <= 0) return
        val widthDp = view.width / resources.displayMetrics.density
        val pagesPerRow = if (widthDp >= TWO_PAGE_MIN_WIDTH_DP) 2 else 1
        if (view.pagesPerRow == pagesPerRow) return
        val firstVisiblePage = view.firstVisiblePage.coerceAtLeast(0)
        view.pagesPerRow = pagesPerRow
        view.horizontalPageSpacing = (PAGE_SPACING_DP * resources.displayMetrics.density)
            .roundToInt()
        view.post {
            if (view.pdfDocument != null) view.scrollToPage(firstVisiblePage)
        }
    }

    private companion object {
        const val TWO_PAGE_MIN_WIDTH_DP = 840f
        const val PAGE_SPACING_DP = 12f
    }
}
