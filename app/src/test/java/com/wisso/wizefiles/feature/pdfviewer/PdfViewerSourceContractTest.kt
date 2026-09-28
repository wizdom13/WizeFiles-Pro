// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.pdfviewer

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewerSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `pdf has a private dedicated internal route`() {
        val policy = source(
            "app/src/main/java/com/wisso/wizefiles/feature/internalviewer/InternalOpenPolicy.kt"
        )
        val router = source(
            "app/src/main/java/com/wisso/wizefiles/feature/internalviewer/InternalOpenIntents.kt"
        )
        val manifest = source("app/src/main/AndroidManifest.xml")

        assertTrue("PDF_VIEWER" in policy)
        assertTrue("application/pdf" in policy)
        assertTrue("PdfViewerIntents.create" in router)
        assertTrue("feature.pdfviewer.PdfViewerActivity" in manifest)
        assertTrue("android:exported=\"false\"" in manifest)
    }

    @Test
    fun `fragment renders staged files with Pdfium zoom and safe links`() {
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/pdfviewer/WizePdfViewerFragment.kt"
        )
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/pdfviewer/PdfViewerActivity.kt"
        )
        val viewModel = source(
            "app/src/main/java/com/wisso/wizefiles/feature/pdfviewer/PdfViewerViewModel.kt"
        )
        val gradle = source("app/build.gradle")

        assertTrue("io.github.oothp:android-pdf-viewer:3.2.0-beta06" in gradle)
        assertFalse("androidx.pdf:pdf-viewer-fragment" in gradle)
        assertTrue("class WizePdfViewerFragment : Fragment()" in fragment)
        assertTrue("PDFView(requireContext(), null)" in fragment)
        assertTrue(".enableDoubletap(true)" in fragment)
        assertTrue(".pageFitPolicy(FitPolicy.WIDTH)" in fragment)
        assertTrue(".fitEachPage(true)" in fragment)
        assertTrue("PdfLinkPolicy.allowsScheme" in fragment)
        assertTrue("onPdfDocumentReady(pageCount)" in fragment)
        assertTrue("onPdfDocumentError(error)" in fragment)

        assertTrue("PdfViewerViewModel.State.Ready" in activity)
        assertTrue("state.session.file" in activity)
        assertTrue("fragment.documentFile" in activity)
        assertTrue("FormatStagingStore" in viewModel)
        assertTrue("source.newInputStream()" in viewModel)
        assertTrue("MAX_STAGED_SOURCE_BYTES" in viewModel)
        assertFalse("Uri.fromFile" in viewModel)
        assertFalse("WebView" in fragment)
        assertFalse("ACTION_ANNOTATE" in fragment)
    }

    @Test
    fun `archive pdf uses the generic guarded extraction boundary`() {
        val externalActions = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListExternalActionController.kt"
        )
        val service = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationCommandCoordinator.kt"
        )
        val executor = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOpenRenameJobs.kt"
        )

        assertTrue("InternalOpenPolicy.targetAfterExtraction(file.mimeType, file.path.name)" in externalActions)
        assertTrue("FileOperationService.openInternalViewer" in externalActions)
        assertTrue("OpenInternalViewerFileOperationJob(file, mimeType)" in service)
        assertTrue("class OpenInternalViewerFileOperationJob(" in executor)
        assertTrue("InternalOpenIntents.create(" in executor)
        assertTrue("MediaPreviewItem(extractedPath, mimeType)" in executor)
    }

    @Test
    fun `viewer actions preserve WizeFiles provider and file contracts`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/pdfviewer/PdfViewerActivity.kt"
        )
        val menu = source("app/src/main/res/menu/menu_pdf_viewer.xml")

        assertTrue("fileProviderUri" in activity)
        assertTrue("createSendStreamIntent" in activity)
        assertTrue("createViewIntent" in activity)
        assertTrue("FilePropertiesDialogFragment.show" in activity)
        assertTrue("action_pdf_share" in menu)
        assertTrue("action_pdf_properties" in menu)
        assertTrue("action_pdf_open_with" in menu)
    }

    private fun source(path: String): String = File(root, path).readText()
}

