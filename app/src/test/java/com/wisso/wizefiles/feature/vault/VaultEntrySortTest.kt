package com.wisso.wizefiles.vault

import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.By
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.Order
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultEntrySortTest {
    private val entries = listOf(
        entry("folder", "Documents", isDirectory = true, size = 0, modifiedAt = 30),
        entry("text", "notes.txt", size = 20, modifiedAt = 20),
        entry("audio", "music.mp3", size = 10, modifiedAt = 10),
        entry("large", "archive.zip", size = 30, modifiedAt = 40)
    )

    @Test
    fun `sort fields and direction match browser behavior`() {
        assertOrder(By.NAME, Order.ASCENDING, false, "archive.zip", "Documents", "music.mp3", "notes.txt")
        assertOrder(By.TYPE, Order.ASCENDING, false, "Documents", "music.mp3", "notes.txt", "archive.zip")
        assertOrder(By.SIZE, Order.DESCENDING, false, "archive.zip", "notes.txt", "music.mp3", "Documents")
        assertOrder(By.LAST_MODIFIED, Order.DESCENDING, false, "archive.zip", "Documents", "notes.txt", "music.mp3")
    }

    @Test
    fun `folders first remains first in descending order`() {
        assertOrder(By.NAME, Order.DESCENDING, true, "Documents", "notes.txt", "music.mp3", "archive.zip")
    }

    private fun assertOrder(
        by: By,
        order: Order,
        directoriesFirst: Boolean,
        vararg expectedNames: String
    ) {
        val actual = sortVaultEntries(
            entries,
            FileSortOptions(by, order, directoriesFirst)
        ).map(VaultEntry::name)
        assertEquals(expectedNames.toList(), actual)
    }

    private fun entry(
        id: String,
        name: String,
        isDirectory: Boolean = false,
        size: Long,
        modifiedAt: Long
    ) = VaultEntry(
        id = id,
        parentId = null,
        name = name,
        isDirectory = isDirectory,
        objectId = if (isDirectory) null else id,
        size = size,
        createdAt = modifiedAt,
        modifiedAt = modifiedAt
    )
}
