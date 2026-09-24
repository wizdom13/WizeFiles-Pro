// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

/**
 * Owns the editor document snapshot together with the text currently being written.
 *
 * A save can complete after the user has typed more text. Keeping the in-flight snapshot here lets
 * the completed write advance only the saved baseline while preserving those newer edits.
 */
internal data class TextEditorSaveState(
    val document: TextEditorDocumentState = TextEditorDocumentState(),
    val inFlightText: String? = null
) {
    val hasUnsavedChanges: Boolean
        get() = document.hasUnsavedChanges

    val canSave: Boolean
        get() = hasUnsavedChanges && inFlightText == null

    fun load(text: String): TextEditorSaveState =
        copy(document = document.load(text), inFlightText = null)

    fun edit(text: String): TextEditorSaveState =
        copy(document = document.edit(text))

    fun beginSave(text: String): TextEditorSaveState {
        check(canSave)
        return copy(inFlightText = text)
    }

    fun completeSave(successful: Boolean): TextEditorSaveState {
        val savedSnapshot = checkNotNull(inFlightText)
        return copy(
            document = if (successful) document.markSaved(savedSnapshot) else document,
            inFlightText = null
        )
    }

    fun discardChanges(): TextEditorSaveState =
        copy(document = document.discardChanges())
}
