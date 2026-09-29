// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncthingImportTransactionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val profiles = mutableSetOf("old")
    private var configuration = "old"
    private var crashInsert = false
    private var crashCleanup = false
    private fun transaction() = SyncthingImportTransaction(temporary.root,
        { profiles.add(it.getString("id")); if (crashInsert) throw AssertionError("process death") },
        { if (crashCleanup) throw AssertionError("process death"); profiles.remove(it) },
        { configuration = it.getString("name") }, { })
    private fun prepare() {
        File(temporary.root, "syncthing").mkdir()
        File(temporary.root, "syncthing/identity").writeText("old")
        transaction().stage.mkdir()
        File(transaction().stage, "identity").writeText("new")
    }
    private fun commit() = transaction().commit(listOf("old"), listOf(JSONObject().put("id", "new")),
        JSONObject().put("name", "old"), JSONObject().put("name", "new"))
    @Test fun processDeathBeforeCommitRestoresHomeConfigurationAndOldProfiles() {
        prepare(); crashInsert = true
        assertThrows(AssertionError::class.java, ::commit)
        crashInsert = false
        transaction().recover()
        assertEquals(setOf("old"), profiles)
        assertEquals("old", configuration)
        assertEquals("old", File(temporary.root, "syncthing/identity").readText())
    }
    @Test fun processDeathAfterCommitFinishesCleanupWithoutRevertingTheImport() {
        prepare(); crashCleanup = true
        assertThrows(AssertionError::class.java, ::commit)
        crashCleanup = false
        transaction().recover()
        assertEquals(setOf("new"), profiles)
        assertEquals("new", configuration)
        assertEquals("new", File(temporary.root, "syncthing/identity").readText())
        assertFalse(File(temporary.root, "syncthing-import-rollback").exists())
        assertFalse(File(temporary.root, "syncthing-import-journal.json").exists())
    }
}
