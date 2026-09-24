package com.wisso.wizefiles.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultEntryNamePolicyTest {
    @Test
    fun `files and folders share one sibling name namespace`() {
        val file = entry("file", "parent", "example", false)
        val folder = entry("folder", "parent", "photos", true)

        assertTrue(VaultEntryNamePolicy.hasConflict(listOf(file), "parent", "example"))
        assertTrue(VaultEntryNamePolicy.hasConflict(listOf(folder), "parent", "photos"))
        assertThrows(VaultEntryNameConflictException::class.java) {
            VaultEntryNamePolicy.requireAvailable(listOf(file), "parent", "example")
        }
        assertThrows(VaultEntryNameConflictException::class.java) {
            VaultEntryNamePolicy.requireAvailable(listOf(folder), "parent", "photos")
        }
    }

    @Test
    fun `matching names in different parents do not conflict`() {
        val entry = entry("entry", "first", "example", false)
        assertFalse(VaultEntryNamePolicy.hasConflict(listOf(entry), "second", "example"))
    }

    @Test
    fun `rename excludes itself but not another sibling`() {
        val current = entry("current", "parent", "example", true)
        val sibling = entry("sibling", "parent", "taken", false)

        assertFalse(
            VaultEntryNamePolicy.hasConflict(
                listOf(current, sibling), "parent", "example", "current"
            )
        )
        assertTrue(
            VaultEntryNamePolicy.hasConflict(
                listOf(current, sibling), "parent", "taken", "current"
            )
        )
    }

    private fun entry(
        id: String,
        parentId: String?,
        name: String,
        isDirectory: Boolean
    ) = VaultEntry(
        id = id,
        parentId = parentId,
        name = name,
        isDirectory = isDirectory,
        objectId = if (isDirectory) null else "object",
        size = 0L,
        createdAt = 0L,
        modifiedAt = 0L
    )
}
