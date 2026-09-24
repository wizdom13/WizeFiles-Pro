// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.batchrename

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRenamePatternEngineTest {
    @Test
    fun `numbering padding names extensions and sizes are expanded`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("Holiday.jpg", size = 42)),
            BatchRenameOptions("%n-###-%S%E", startNumber = 7)
        )

        assertTrue(plan.isValid)
        assertEquals("Holiday-007-42.jpg", plan.rows.single().targetName)
    }

    @Test
    fun `full name date time and escaped percent are expanded`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("note.txt", modified = null)),
            BatchRenameOptions("%N-%D-%T-%%")
        )

        assertEquals("note.txt-unknown-unknown-%", plan.rows.single().targetName)
    }

    @Test
    fun `plain replacement changes only base name`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("IMG_IMG_100.jpg")),
            BatchRenameOptions("Product_%n%E", "IMG_", "")
        )

        assertEquals("Product_100.jpg", plan.rows.single().targetName)
    }

    @Test
    fun `token characters inside original names remain literal`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("C# %D guide.txt")),
            BatchRenameOptions("%n-##%E")
        )

        assertEquals("C# %D guide-01.txt", plan.rows.single().targetName)
    }

    @Test
    fun `regex replacement supports capture groups`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("Photo 2026.jpg")),
            BatchRenameOptions("%n%E", "Photo (\\d+)", "Holiday_\$1", true)
        )

        assertEquals("Holiday_2026.jpg", plan.rows.single().targetName)
    }

    @Test
    fun `invalid regex is reported before preview rows`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("a.txt")),
            BatchRenameOptions("%n%E", "[", "", true)
        )

        assertTrue(plan.globalError!!.startsWith("Invalid regular expression"))
        assertTrue(plan.rows.isEmpty())
    }

    @Test
    fun `compound extensions stay together`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("backup.tar.gz")),
            BatchRenameOptions("Archive_%n%E")
        )

        assertEquals("Archive_backup.tar.gz", plan.rows.single().targetName)
    }

    @Test
    fun `apk tokens use application and version metadata`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("base.apk", apkLabel = "WizeFiles", apkVersion = "1.2.0")),
            BatchRenameOptions("%A-%V%E")
        )

        assertEquals("WizeFiles-1.2.0.apk", plan.rows.single().targetName)
        assertNull(plan.rows.single().error)
    }

    @Test
    fun `missing apk metadata blocks rename`() {
        val plan = BatchRenamePatternEngine.plan(
            listOf(source("base.apk")),
            BatchRenameOptions("%A%E")
        )

        assertFalse(plan.isValid)
        assertEquals("APK application or version information is unavailable", plan.rows.single().error)
    }

    @Test
    fun `duplicate and existing targets block rename`() {
        val duplicates = BatchRenamePatternEngine.plan(
            listOf(source("a.txt"), source("b.txt")),
            BatchRenameOptions("same.txt")
        )
        assertTrue(duplicates.rows.all { it.error == "Duplicate resulting name" })

        val existing = BatchRenamePatternEngine.plan(
            listOf(source("a.txt")),
            BatchRenameOptions("occupied.txt"),
            existingNames = listOf("a.txt", "Occupied.TXT")
        )
        assertEquals("A file with this name already exists", existing.rows.single().error)
    }

    @Test
    fun `case only rename is valid`() {
        val caseOnly = BatchRenamePatternEngine.plan(
            listOf(source("PHOTO.JPG")),
            BatchRenameOptions("photo.JPG"),
            existingNames = listOf("PHOTO.JPG")
        )
        assertTrue(caseOnly.isValid)

    }

    @Test
    fun `hundreds of rows keep deterministic order and padding`() {
        val sources = (1..250).map { source("IMG_$it.jpg") }
        val plan = BatchRenamePatternEngine.plan(
            sources,
            BatchRenameOptions("Photo_###%E")
        )

        assertTrue(plan.isValid)
        assertEquals(250, plan.rows.size)
        assertEquals("Photo_001.jpg", plan.rows.first().targetName)
        assertEquals("Photo_250.jpg", plan.rows.last().targetName)
        assertEquals("%n (###)%E", BatchRenamePatternEngine.defaultPattern(250))
    }

    private fun source(
        name: String,
        modified: Long? = null,
        size: Long = 0,
        apkLabel: String? = null,
        apkVersion: String? = null
    ) = BatchRenameSource(name, modified, size, apkLabel, apkVersion)
}
