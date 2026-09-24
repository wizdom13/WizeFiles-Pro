// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import org.json.JSONArray
import org.json.JSONObject

data class Argon2Params(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val outputLength: Int = 32
) {
    fun toJson(): JSONObject = JSONObject()
        .put("memoryKiB", memoryKiB)
        .put("iterations", iterations)
        .put("parallelism", parallelism)
        .put("outputLength", outputLength)

    companion object {
        fun fromJson(json: JSONObject) = Argon2Params(
            memoryKiB = json.getInt("memoryKiB"),
            iterations = json.getInt("iterations"),
            parallelism = json.getInt("parallelism"),
            outputLength = json.optInt("outputLength", 32)
        )
    }
}

data class VaultMetadata(
    val version: Int,
    val vaultId: String,
    val name: String,
    val createdAt: Long,
    val saltBase64: String,
    val argon2Params: Argon2Params,
    val wrappedVmkBase64: String,
    val vmkWrapIvBase64: String,
    val biometricEnabled: Boolean,
    val biometricWrappedVmkBase64: String?,
    val biometricIvBase64: String?,
    val biometricKeyAlias: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("vaultId", vaultId)
        .put("name", name)
        .put("createdAt", createdAt)
        .put("saltBase64", saltBase64)
        .put("argon2Params", argon2Params.toJson())
        .put("wrappedVmkBase64", wrappedVmkBase64)
        .put("vmkWrapIvBase64", vmkWrapIvBase64)
        .put("biometricEnabled", biometricEnabled)
        .put("biometricWrappedVmkBase64", biometricWrappedVmkBase64)
        .put("biometricIvBase64", biometricIvBase64)
        .put("biometricKeyAlias", biometricKeyAlias)

    companion object {
        fun fromJson(json: JSONObject) = VaultMetadata(
            version = json.getInt("version"),
            vaultId = json.getString("vaultId"),
            name = json.getString("name"),
            createdAt = json.getLong("createdAt"),
            saltBase64 = json.getString("saltBase64"),
            argon2Params = Argon2Params.fromJson(json.getJSONObject("argon2Params")),
            wrappedVmkBase64 = json.getString("wrappedVmkBase64"),
            vmkWrapIvBase64 = json.getString("vmkWrapIvBase64"),
            biometricEnabled = json.optBoolean("biometricEnabled", false),
            biometricWrappedVmkBase64 = json.optNullableString("biometricWrappedVmkBase64"),
            biometricIvBase64 = json.optNullableString("biometricIvBase64"),
            biometricKeyAlias = json.optString("biometricKeyAlias", "vault_key")
        )
    }
}

data class VaultEntry(
    override val id: String,
    override val parentId: String?,
    override val name: String,
    val isDirectory: Boolean,
    val objectId: String?,
    val size: Long,
    val createdAt: Long,
    val modifiedAt: Long,
) : VaultEntryIdentity {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("parentId", parentId)
        .put("name", name)
        .put("isDirectory", isDirectory)
        .put("objectId", objectId)
        .put("size", size)
        .put("createdAt", createdAt)
        .put("modifiedAt", modifiedAt)

    companion object {
        fun fromJson(json: JSONObject) = VaultEntry(
            id = json.getString("id"),
            parentId = json.optNullableString("parentId"),
            name = json.getString("name"),
            isDirectory = json.getBoolean("isDirectory"),
            objectId = json.optNullableString("objectId"),
            size = json.optLong("size", 0L),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            modifiedAt = json.optLong("modifiedAt", System.currentTimeMillis())
        )
    }
}

fun List<VaultEntry>.toJsonArray(): JSONArray = JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }

fun JSONArray.toVaultEntries(): List<VaultEntry> = buildList {
    for (i in 0 until length()) {
        add(VaultEntry.fromJson(getJSONObject(i)))
    }
}


private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null
