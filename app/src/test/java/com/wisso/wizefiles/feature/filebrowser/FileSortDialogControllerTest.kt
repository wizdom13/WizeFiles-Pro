package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortDialogControllerTest {
    @Test
    fun `all persisted choices have stable radio mappings`() = with(FileSortDialogController) {
        FileViewType.entries.forEach { assertEquals(it, it.toRadioId().toViewType()) }
        FileSortOptions.By.entries.forEach { assertEquals(it, it.toRadioId().toSortBy()) }
        FileSortOptions.Order.entries.forEach { assertEquals(it, it.toRadioId().toSortOrder()) }
    }

    @Test
    fun `unknown widget ids resolve to safe defaults`() = with(FileSortDialogController) {
        assertEquals(FileViewType.LIST, R.id.sort_by_group.toViewType())
        assertEquals(FileSortOptions.By.NAME, R.id.sort_by_group.toSortBy())
        assertEquals(FileSortOptions.Order.ASCENDING, R.id.sort_by_group.toSortOrder())
    }
}
