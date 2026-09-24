package com.wisso.wizefiles.feature.apksigning

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.io.IOException
import org.json.JSONObject

internal object AabSigningOperationStore {
    private val directory: File
        get() = File(application.noBackupFilesDir, "aab-signing-operations").apply { mkdirs() }

    @Synchronized
    fun save(spec: AabSigningWorkflowSpec) {
        val target = File(directory, "${spec.operationId}.json")
        val temporary = File(directory, "${spec.operationId}.json.tmp")
        temporary.writeText(spec.toJson().toString())
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to persist AAB signing operation")
        }
    }

    @Synchronized
    fun load(operationId: String): AabSigningWorkflowSpec? {
        val file = File(directory, "$operationId.json")
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()).toSpec() }.getOrNull()
    }

    @Synchronized
    fun delete(operationId: String) {
        File(directory, "$operationId.json").delete()
        File(directory, "$operationId.json.tmp").delete()
    }

    private fun AabSigningWorkflowSpec.toJson() = JSONObject()
        .put("operationId", operationId)
        .put("sourceUri", sourceUri)
        .put("outputUri", outputUri)
        .put("signingKeyUri", signingKeyUri)
        .put("certificateUri", certificateUri)
        .put("keySource", keySource.name)
        .put("keyAlias", keyAlias)
        .put("keyStoreFormat", keyStoreFormat.name)
        .put("conflictPolicy", conflictPolicy.name)

    private fun JSONObject.toSpec() = AabSigningWorkflowSpec(
        operationId = getString("operationId"),
        sourceUri = getString("sourceUri"),
        outputUri = getString("outputUri"),
        signingKeyUri = getString("signingKeyUri"),
        certificateUri = optString("certificateUri"),
        keySource = AabSigningKeySource.valueOf(getString("keySource")),
        keyAlias = optString("keyAlias"),
        keyStoreFormat = ApkKeyStoreFormat.valueOf(getString("keyStoreFormat")),
        conflictPolicy = ApkSigningOutputConflictPolicy.valueOf(getString("conflictPolicy"))
    )
}
