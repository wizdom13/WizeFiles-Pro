// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

import java.nio.file.AccessDeniedException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorWriteAccessTest {
    @Test
    fun `write capability accepts successful provider check`() {
        assertTrue(writeAccessGranted { })
    }

    @Test
    fun `write capability rejects provider denial or revocation`() {
        assertFalse(writeAccessGranted { throw AccessDeniedException("read-only") })
    }

    @Test
    fun `local denial requests access while remote and read-only providers fail safely`() {
        assertTrue(
            decideTextEditorWrite(false, isLocalPath = true, canRequestAllFilesAccess = true) ==
                TextEditorWriteDecision.REQUEST_ALL_FILES_ACCESS
        )
        assertTrue(
            decideTextEditorWrite(false, isLocalPath = false, canRequestAllFilesAccess = true) ==
                TextEditorWriteDecision.READ_ONLY
        )
        assertTrue(
            decideTextEditorWrite(false, isLocalPath = true, canRequestAllFilesAccess = false) ==
                TextEditorWriteDecision.READ_ONLY
        )
    }

    @Test
    fun `revocation never proceeds to write`() {
        assertTrue(
            decideTextEditorWrite(false, isLocalPath = false, canRequestAllFilesAccess = false) !=
                TextEditorWriteDecision.WRITE
        )
    }
}
