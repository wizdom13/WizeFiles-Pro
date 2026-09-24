package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class FileListSavedStateTest {
    @Test
    fun `search UI restores without persisting paths or credentials`() {
        val state = SavedStateHandle(
            mapOf(
                "browser.searchExpanded" to true,
                "browser.searchViewQuery" to "report"
            )
        )
        BrowserSearchSavedState(state).apply {
            isSearching = true
            query = "report"
        }

        val restored = BrowserSearchSavedState(state)
        assertTrue(restored.isSearching)
        assertEquals("report", restored.query)
        assertTrue(restored.expanded.value == true)
        assertEquals("report", restored.viewQuery.value)
        assertTrue(state.keys().none { it.contains("path", ignoreCase = true) })
    }
}
