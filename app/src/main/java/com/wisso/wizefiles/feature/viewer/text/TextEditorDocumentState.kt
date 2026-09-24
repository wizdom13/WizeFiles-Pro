package com.wisso.wizefiles.viewer.text

/**
 * Tracks the text displayed by the editor separately from the last successfully loaded or saved
 * snapshot. Keeping both snapshots prevents a completed write from clearing changes typed while
 * that write was still running.
 */
internal data class TextEditorDocumentState(
    val savedText: String? = null,
    val currentText: String? = null
) {
    val hasUnsavedChanges: Boolean
        get() = savedText != null && currentText != null && currentText != savedText

    fun load(text: String): TextEditorDocumentState =
        TextEditorDocumentState(savedText = text, currentText = text)

    fun edit(text: String): TextEditorDocumentState = copy(currentText = text)

    fun markSaved(text: String): TextEditorDocumentState = copy(savedText = text)

    fun discardChanges(): TextEditorDocumentState = copy(currentText = savedText)
}
