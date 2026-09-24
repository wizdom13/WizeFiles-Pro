package com.wisso.wizefiles.feature.apksigning

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

internal object ApksSigningOperationStore {
    private val directory: File
        get() = File(application.noBackupFilesDir, "apks-signing-operations").apply { mkdirs() }

    @Synchronized
    fun save(spec: ApksSigningWorkflowSpec) {
        val target = File(directory, "${spec.operationId}.json")
        val temporary = File(directory, "${spec.operationId}.json.tmp")
        temporary.writeText(spec.toJson().toString())
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to persist APKS signing operation")
        }
    }

    @Synchronized
    fun load(operationId: String): ApksSigningWorkflowSpec? {
        val file = File(directory, "$operationId.json")
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()).toSpec() }.getOrNull()
    }

    @Synchronized
    fun delete(operationId: String) {
        File(directory, "$operationId.json").delete()
        File(directory, "$operationId.json.tmp").delete()
    }

    private fun ApksSigningWorkflowSpec.toJson() = JSONObject()
        .put("operationId", operationId)
        .put("sourceUri", sourceUri)
        .put("outputUri", outputUri)
        .put("signingKeyUri", signingKeyUri)
        .put("certificateUri", certificateUri)
        .put("keySource", keySource.name)
        .put("keyAlias", keyAlias)
        .put("keyStoreFormat", keyStoreFormat.name)
        .put("schemes", JSONArray(schemes.map(ApkSignatureScheme::name)))
        .put("conflictPolicy", conflictPolicy.name)

    private fun JSONObject.toSpec() = ApksSigningWorkflowSpec(
        operationId = getString("operationId"),
        sourceUri = getString("sourceUri"),
        outputUri = getString("outputUri"),
        signingKeyUri = getString("signingKeyUri"),
        certificateUri = optString("certificateUri"),
        keySource = AabSigningKeySource.valueOf(getString("keySource")),
        keyAlias = optString("keyAlias"),
        keyStoreFormat = ApkKeyStoreFormat.valueOf(getString("keyStoreFormat")),
        schemes = getJSONArray("schemes").let { array ->
            buildSet {
                for (index in 0 until array.length()) {
                    add(ApkSignatureScheme.valueOf(array.getString(index)))
                }
            }
        },
        conflictPolicy = ApkSigningOutputConflictPolicy.valueOf(getString("conflictPolicy"))
    )
}
