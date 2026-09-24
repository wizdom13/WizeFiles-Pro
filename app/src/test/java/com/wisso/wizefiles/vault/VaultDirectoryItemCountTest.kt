package com.wisso.wizefiles.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultDirectoryItemCountTest {
    @Test
    fun `counts direct children and includes empty directories`() {
        val root = entry("root", null, true)
        val empty = entry("empty", null, true)
        val nested = entry("nested", "root", true)
        val file = entry("file", "root", false)
        val deepFile = entry("deep", "nested", false)

        val counts = countVaultDirectoryItems(listOf(root, empty, nested, file, deepFile))

        assertEquals(2, counts.getValue("root"))
        assertEquals(0, counts.getValue("empty"))
        assertEquals(1, counts.getValue("nested"))
    }

    private fun entry(id: String, parentId: String?, directory: Boolean) = VaultEntry(
        id = id,
        parentId = parentId,
        name = id,
        isDirectory = directory,
        objectId = null,
        size = 0,
        createdAt = 0,
        modifiedAt = 0
    )
}
