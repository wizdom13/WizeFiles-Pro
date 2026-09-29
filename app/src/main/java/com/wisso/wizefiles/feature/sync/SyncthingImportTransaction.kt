// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/** Old profiles/history survive until commit. A journal repairs interruption across DB and file swaps. */
internal class SyncthingImportTransaction(
    private val base: File,
    private val insertProfile: (JSONObject) -> Unit,
    private val deleteProfile: (String) -> Unit,
    private val writeConfiguration: (JSONObject) -> Unit,
    private val scheduleProfile: (JSONObject) -> Unit
) {
    val stage = File(base, "syncthing-import-stage")
    private val home = File(base, "syncthing")
    private val rollback = File(base, "syncthing-import-rollback")
    private val journalFile = File(base, "syncthing-import-journal.json")
    private val journal = SyncthingJsonStore(journalFile)

    fun commit(oldIds: List<String>, profiles: List<JSONObject>, previous: JSONObject, replacement: JSONObject) {
        check(!journalFile.exists() && !rollback.exists() && stage.isDirectory)
        check(profiles.map { it.getString("id") }.none { it in oldIds })
        journal.update { root ->
            root.put("oldIds", JSONArray(oldIds)).put("profiles", JSONArray(profiles))
                .put("configuration", previous).put("hadHome", home.exists()).put("committed", false)
        }
        try {
            if (home.exists()) move(home, rollback)
            move(stage, home)
            writeConfiguration(replacement)
            profiles.forEach(insertProfile)
            journal.update { it.put("committed", true) }
        } catch (failure: Exception) {
            try { recover() } catch (recovery: Exception) { failure.addSuppressed(recovery) }
            throw failure
        }
        recover()
    }

    fun recover() {
        if (!journalFile.exists()) return
        val saved = journal.read()
        val profiles = saved.getJSONArray("profiles").objects()
        if (saved.getBoolean("committed")) {
            saved.getJSONArray("oldIds").strings().forEach(deleteProfile)
            profiles.forEach(scheduleProfile)
            remove(rollback)
        } else {
            profiles.forEach { deleteProfile(it.getString("id")) }
            writeConfiguration(saved.getJSONObject("configuration"))
            if (rollback.exists()) {
                remove(home)
                move(rollback, home)
            } else if (!saved.getBoolean("hadHome")) remove(home)
        }
        remove(stage)
        check(journalFile.delete()) { "Could not finalize Syncthing import recovery" }
    }

    fun discardStage() { check(!journalFile.exists()); remove(stage) }
    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
    private fun remove(file: File) {
        if (file.exists()) check(file.deleteRecursively()) { "Could not clean up Syncthing import data" }
    }
}
