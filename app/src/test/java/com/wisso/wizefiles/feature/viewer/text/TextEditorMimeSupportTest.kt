package com.wisso.wizefiles.viewer.text

import com.wisso.wizefiles.core.files.mime.asMimeType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorMimeSupportTest {
    @Test
    fun `supports text and code mime types exposed by the editor`() {
        assertTrue("text/plain".asMimeType().isTextEditorSupported)
        assertTrue("text/markdown".asMimeType().isTextEditorSupported)
        assertTrue("application/json".asMimeType().isTextEditorSupported)
        assertTrue("application/xml".asMimeType().isTextEditorSupported)
        assertTrue("application/x-sh".asMimeType().isTextEditorSupported)
    }

    @Test
    fun `rejects non text files`() {
        assertFalse("application/pdf".asMimeType().isTextEditorSupported)
        assertFalse("image/png".asMimeType().isTextEditorSupported)
        assertFalse("application/zip".asMimeType().isTextEditorSupported)
    }
}
