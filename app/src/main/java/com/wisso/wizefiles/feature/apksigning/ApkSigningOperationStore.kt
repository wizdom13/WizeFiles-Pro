// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

internal object ApkSigningOperationStore {
    private val directory: File
        get() = File(application.noBackupFilesDir, "apk-signing-operations").apply { mkdirs() }

    @Synchronized
    fun save(spec: ApkSigningWorkflowSpec) {
        val target = File(directory, "${spec.operationId}.json")
        val temporary = File(directory, "${spec.operationId}.json.tmp")
        temporary.writeText(ApkSigningOperationCodec.encode(spec))
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to persist APK signing operation")
        }
    }

    @Synchronized
    fun load(operationId: String): ApkSigningWorkflowSpec? {
        val file = File(directory, "$operationId.json")
        if (!file.isFile) return null
        return runCatching { ApkSigningOperationCodec.decode(file.readText()) }.getOrNull()
    }

    @Synchronized
    fun delete(operationId: String) {
        File(directory, "$operationId.json").delete()
        File(directory, "$operationId.json.tmp").delete()
    }
}

/** Durable codec deliberately accepts workflow metadata only; signing secrets have no input path. */
internal object ApkSigningOperationCodec {
    fun encode(spec: ApkSigningWorkflowSpec): String = spec.toJson().toString()

    fun decode(encoded: String): ApkSigningWorkflowSpec = JSONObject(encoded).toSpec()

    private fun ApkSigningWorkflowSpec.toJson() = JSONObject()
        .put("operationId", operationId)
        .put("sourceUri", sourceUri)
        .put("outputUri", outputUri)
        .put("keyStoreUri", keyStoreUri)
        .put("keyAlias", keyAlias)
        .put("keyStoreFormat", keyStoreFormat.name)
        .put("schemes", JSONArray().also { array -> schemes.forEach { array.put(it.name) } })
        .put("minSdkVersion", minSdkVersion ?: JSONObject.NULL)
        .put("conflictPolicy", conflictPolicy.name)

    private fun JSONObject.toSpec(): ApkSigningWorkflowSpec {
        val schemeArray = getJSONArray("schemes")
        val schemes = buildSet {
            for (index in 0 until schemeArray.length()) {
                add(ApkSignatureScheme.valueOf(schemeArray.getString(index)))
            }
        }
        return ApkSigningWorkflowSpec(
            operationId = getString("operationId"),
            sourceUri = getString("sourceUri"),
            outputUri = getString("outputUri"),
            keyStoreUri = getString("keyStoreUri"),
            keyAlias = optString("keyAlias"),
            keyStoreFormat = ApkKeyStoreFormat.valueOf(getString("keyStoreFormat")),
            schemes = schemes,
            minSdkVersion = if (isNull("minSdkVersion")) null else getInt("minSdkVersion"),
            conflictPolicy = ApkSigningOutputConflictPolicy.valueOf(getString("conflictPolicy"))
        )
    }
}
