// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorDocumentStateTest {
    @Test
    fun `loaded text starts clean and an edit becomes dirty`() {
        val loaded = TextEditorDocumentState().load("original")

        assertFalse(loaded.hasUnsavedChanges)
        assertTrue(loaded.edit("changed").hasUnsavedChanges)
    }

    @Test
    fun `saving the current snapshot clears dirty state`() {
        val changed = TextEditorDocumentState().load("original").edit("changed")

        assertFalse(changed.markSaved("changed").hasUnsavedChanges)
    }

    @Test
    fun `edits made during a save remain dirty after that snapshot succeeds`() {
        val saving = TextEditorDocumentState().load("original").edit("first change")
        val editedWhileSaving = saving.edit("second change")

        assertTrue(editedWhileSaving.markSaved("first change").hasUnsavedChanges)
    }

    @Test
    fun `returning to the saved text clears dirty state`() {
        val changed = TextEditorDocumentState().load("original").edit("changed")

        assertFalse(changed.edit("original").hasUnsavedChanges)
        assertFalse(changed.discardChanges().hasUnsavedChanges)
    }
}
