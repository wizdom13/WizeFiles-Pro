package com.wisso.wizefiles.feature.internalviewer

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.advancedformats.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class InternalOpenPolicyTest {
    @Test
    fun `supported file families use their focused internal viewers`() {
        mapOf(
            "image/jpeg" to InternalOpenPolicy.Target.IMAGE_PREVIEW,
            "video/mp4" to InternalOpenPolicy.Target.VIDEO_PREVIEW,
            "audio/mpeg" to InternalOpenPolicy.Target.AUDIO_PLAYER,
            "application/pdf" to InternalOpenPolicy.Target.PDF_VIEWER,
            "application/epub+zip" to InternalOpenPolicy.Target.EBOOK_VIEWER,
            "application/vnd.ms-htmlhelp" to InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER
        ).forEach { (mimeType, expected) ->
            assertEquals(
                expected,
                InternalOpenPolicy.targetFor(MimeType(mimeType), isArchiveEntry = false)
            )
        }
    }

    @Test
    fun `advanced image extensions override generic mime after guarded routing`() {
        listOf("icon.ico", "scan.tiff", "camera.dng", "texture.tga").forEach { name ->
            assertEquals(
                InternalOpenPolicy.Target.IMAGE_PREVIEW,
                InternalOpenPolicy.targetFor(
                    MimeType("application/octet-stream"),
                    isArchiveEntry = false,
                    fileName = name
                )
            )
            assertEquals(
                InternalOpenPolicy.Target.EXTERNAL_APP,
                InternalOpenPolicy.targetFor(
                    MimeType("application/octet-stream"),
                    isArchiveEntry = true,
                    fileName = name
                )
            )
        }
    }

    @Test
    fun `media and pdf extensions override generic mime after guarded routing`() {
        mapOf(
            "concert.wv" to InternalOpenPolicy.Target.AUDIO_PLAYER,
            "master.dsf" to InternalOpenPolicy.Target.AUDIO_PLAYER,
            "capture.mxf" to InternalOpenPolicy.Target.VIDEO_PREVIEW,
            "movie.rmvb" to InternalOpenPolicy.Target.VIDEO_PREVIEW,
            "manual.pdf" to InternalOpenPolicy.Target.PDF_VIEWER
        ).forEach { (name, expected) ->
            assertEquals(
                expected,
                InternalOpenPolicy.targetFor(
                    MimeType("application/octet-stream"),
                    isArchiveEntry = false,
                    fileName = name
                )
            )
            assertEquals(
                InternalOpenPolicy.Target.EXTERNAL_APP,
                InternalOpenPolicy.targetFor(
                    MimeType("application/octet-stream"),
                    isArchiveEntry = true,
                    fileName = name
                )
            )
        }
    }

    @Test
    fun `unknown documents remain external`() {
        listOf(
            "text/plain",
            "application/octet-stream"
        ).forEach { value ->
            assertEquals(
                InternalOpenPolicy.Target.EXTERNAL_APP,
                InternalOpenPolicy.targetFor(MimeType(value), isArchiveEntry = false)
            )
        }
    }

    @Test
    fun `archive entries stay external until guarded extraction`() {
        listOf(
            "image/png", "video/webm", "audio/ogg", "application/pdf", "application/epub+zip",
            "application/vnd.ms-htmlhelp"
        ).forEach { value ->
            assertEquals(
                InternalOpenPolicy.Target.EXTERNAL_APP,
                InternalOpenPolicy.targetFor(MimeType(value), isArchiveEntry = true)
            )
        }
        assertEquals(
            InternalOpenPolicy.Target.IMAGE_PREVIEW,
            InternalOpenPolicy.targetAfterExtraction(MimeType("image/png"))
        )
        assertEquals(
            InternalOpenPolicy.Target.VIDEO_PREVIEW,
            InternalOpenPolicy.targetAfterExtraction(MimeType("video/webm"))
        )
        assertEquals(
            InternalOpenPolicy.Target.AUDIO_PLAYER,
            InternalOpenPolicy.targetAfterExtraction(MimeType("audio/ogg"))
        )
        assertEquals(
            InternalOpenPolicy.Target.PDF_VIEWER,
            InternalOpenPolicy.targetAfterExtraction(MimeType("application/pdf"))
        )
        assertEquals(
            InternalOpenPolicy.Target.EBOOK_VIEWER,
            InternalOpenPolicy.targetAfterExtraction(MimeType("application/epub+zip"))
        )
        assertEquals(
            InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER,
            InternalOpenPolicy.targetAfterExtraction(
                MimeType("application/octet-stream"),
                "page.mhtml"
            )
        )
    }

    @Test
    fun `registry exposes explicit destinations for later focused PRs`() {
        assertEquals(
            InternalOpenPolicy.Target.EBOOK_VIEWER,
            InternalOpenPolicy.plannedTargetFor(FileFormat.EPUB)
        )
        assertEquals(
            InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER,
            InternalOpenPolicy.plannedTargetFor(FileFormat.CHM)
        )
        assertEquals(
            InternalOpenPolicy.Target.CONTAINER_BROWSER,
            InternalOpenPolicy.plannedTargetFor(FileFormat.VHDX)
        )
        assertEquals(
            InternalOpenPolicy.Target.IMAGE_PREVIEW,
            InternalOpenPolicy.plannedTargetFor(FileFormat.CAMERA_RAW)
        )
    }
}

