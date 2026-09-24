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
    fun `fragment owns reading search safe links and adaptive pages`() {
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/pdfviewer/WizePdfViewerFragment.kt"
        )
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/pdfviewer/PdfViewerActivity.kt"
        )
        val gradle = source("app/build.gradle")

        assertTrue("androidx.pdf:pdf-viewer-fragment:1.0.0-alpha19" in gradle)
        assertTrue("compileSdkExtension = 19" in gradle)
        assertTrue("class WizePdfViewerFragment : PdfViewerFragment()" in fragment)
        assertTrue("documentUri" in activity)
        assertTrue("isTextSearchActive" in fragment)
        assertTrue("override fun onLinkClicked" in fragment)
        assertTrue("PdfLinkPolicy.allowsScheme" in fragment)
        assertTrue("view.pagesPerRow = pagesPerRow" in fragment)
        assertTrue("isToolboxVisible = false" in fragment)
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
        assertTrue("action_pdf_search" in menu)
        assertTrue("action_pdf_share" in menu)
        assertTrue("action_pdf_properties" in menu)
        assertTrue("action_pdf_open_with" in menu)
    }

    private fun source(path: String): String = File(root, path).readText()
}

