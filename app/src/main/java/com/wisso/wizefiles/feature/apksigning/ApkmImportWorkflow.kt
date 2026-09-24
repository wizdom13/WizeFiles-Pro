package com.wisso.wizefiles.feature.apksigning

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONObject

data class ApkmImportWorkflowSpec(
    val operationId: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val outputUri: String,
    val conflictPolicy: ApkSigningOutputConflictPolicy =
        ApkSigningOutputConflictPolicy.KEEP_BOTH
) {
    init {
        require(operationId.isNotBlank()) { "An import operation ID is required" }
        require(sourceUri.isNotBlank()) { "An input APKM is required" }
        require(outputUri.isNotBlank()) { "An output APKS is required" }
        require(sourceUri != outputUri) { "The original APKM cannot be overwritten" }
    }
}

internal object ApkmImportOperationStore {
    private val directory: File
        get() = File(application.noBackupFilesDir, "apkm-import-operations").apply { mkdirs() }

    @Synchronized
    fun save(spec: ApkmImportWorkflowSpec) {
        val target = File(directory, "${spec.operationId}.json")
        val temporary = File(directory, "${spec.operationId}.json.tmp")
        temporary.writeText(JSONObject()
            .put("operationId", spec.operationId)
            .put("sourceUri", spec.sourceUri)
            .put("outputUri", spec.outputUri)
            .put("conflictPolicy", spec.conflictPolicy.name)
            .toString())
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to persist APKM import operation")
        }
    }

    @Synchronized
    fun load(operationId: String): ApkmImportWorkflowSpec? {
        val file = File(directory, "$operationId.json")
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            ApkmImportWorkflowSpec(
                operationId = json.getString("operationId"),
                sourceUri = json.getString("sourceUri"),
                outputUri = json.getString("outputUri"),
                conflictPolicy = ApkSigningOutputConflictPolicy.valueOf(
                    json.getString("conflictPolicy")
                )
            )
        }.getOrNull()
    }

    @Synchronized
    fun delete(operationId: String) {
        File(directory, "$operationId.json").delete()
        File(directory, "$operationId.json.tmp").delete()
    }
}
