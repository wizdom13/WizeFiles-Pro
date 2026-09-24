// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.editor

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

object ArchiveMutationStore {
    data class CommitJournal(
        val original: String,
        val temporary: String,
        val backup: String,
        val phase: String
    )
    private val directory: File
        get() = File(application.noBackupFilesDir, "archive-edits").apply { mkdirs() }

    @Synchronized
    fun save(spec: ArchiveMutationSpec) {
        val target = File(directory, "${spec.operationId}.json")
        val temporary = File(directory, "${spec.operationId}.json.tmp")
        temporary.writeText(spec.toJson().toString())
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to persist archive edit")
        }
    }

    @Synchronized
    fun load(operationId: String): ArchiveMutationSpec? {
        val file = File(directory, "$operationId.json")
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()).toSpec() }.getOrNull()
    }

    @Synchronized
    fun updatePhase(operationId: String, phase: ArchiveEditPhase) {
        val spec = load(operationId) ?: return
        save(spec.copy(phase = phase))
    }

    @Synchronized
    fun delete(operationId: String) {
        File(directory, "$operationId.json").delete()
        File(directory, "$operationId.json.tmp").delete()
        File(directory, "$operationId.journal.json").delete()
    }

    @Synchronized
    fun saveJournal(operationId: String, original: String, temporary: String, backup: String, phase: String) {
        File(directory, "$operationId.journal.json").writeText(
            JSONObject()
                .put("original", original)
                .put("temporary", temporary)
                .put("backup", backup)
                .put("phase", phase)
                .toString()
        )
    }

    @Synchronized
    fun loadJournal(operationId: String): CommitJournal? {
        val file = File(directory, "$operationId.journal.json")
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            CommitJournal(
                json.getString("original"),
                json.getString("temporary"),
                json.getString("backup"),
                json.getString("phase")
            )
        }.getOrNull()
    }

    private fun ArchiveMutationSpec.toJson() = JSONObject()
        .put("operationId", operationId)
        .put("archiveUri", archiveUri)
        .put("conflictPolicy", conflictPolicy.name)
        .put("originalFingerprint", originalFingerprint)
        .put("phase", phase.name)
        .put("mutations", JSONArray().also { array ->
            mutations.forEach { mutation ->
                array.put(
                    JSONObject()
                        .put("type", mutation.type.name)
                        .put("path", mutation.path)
                        .put("targetPath", mutation.targetPath)
                        .put("sourceUri", mutation.sourceUri)
                        .put("deleteSourceAfterCommit", mutation.deleteSourceAfterCommit)
                )
            }
        })

    private fun JSONObject.toSpec(): ArchiveMutationSpec {
        val array = getJSONArray("mutations")
        val mutations = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ArchiveMutation(
                        type = ArchiveMutationType.valueOf(item.getString("type")),
                        path = item.getString("path"),
                        targetPath = item.optString("targetPath"),
                        sourceUri = item.optString("sourceUri"),
                        deleteSourceAfterCommit = item.optBoolean("deleteSourceAfterCommit")
                    )
                )
            }
        }
        return ArchiveMutationSpec(
            operationId = getString("operationId"),
            archiveUri = getString("archiveUri"),
            mutations = mutations,
            conflictPolicy = ArchiveConflictPolicy.valueOf(getString("conflictPolicy")),
            originalFingerprint = optString("originalFingerprint"),
            phase = ArchiveEditPhase.valueOf(optString("phase", ArchiveEditPhase.PLANNING.name))
        )
    }
}
