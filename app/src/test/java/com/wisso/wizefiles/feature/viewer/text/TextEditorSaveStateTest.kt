// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorSaveStateTest {
    @Test
    fun `an edit enables save`() {
        val state = TextEditorSaveState().load("original").edit("changed")

        assertTrue(state.hasUnsavedChanges)
        assertTrue(state.canSave)
    }

    @Test
    fun `save is unavailable only while a write is in flight`() {
        val saving = TextEditorSaveState()
            .load("original")
            .edit("changed")
            .beginSave("changed")

        assertTrue(saving.hasUnsavedChanges)
        assertFalse(saving.canSave)
    }

    @Test
    fun `successful write clears dirty state when no newer edits exist`() {
        val saved = TextEditorSaveState()
            .load("original")
            .edit("changed")
            .beginSave("changed")
            .completeSave(successful = true)

        assertFalse(saved.hasUnsavedChanges)
        assertFalse(saved.canSave)
    }

    @Test
    fun `newer edits become saveable when an earlier write completes`() {
        val completed = TextEditorSaveState()
            .load("original")
            .edit("first change")
            .beginSave("first change")
            .edit("second change")
            .completeSave(successful = true)

        assertTrue(completed.hasUnsavedChanges)
        assertTrue(completed.canSave)
    }

    @Test
    fun `failed write keeps changes dirty and re-enables save`() {
        val failed = TextEditorSaveState()
            .load("original")
            .edit("changed")
            .beginSave("changed")
            .completeSave(successful = false)

        assertTrue(failed.hasUnsavedChanges)
        assertTrue(failed.canSave)
    }
}
