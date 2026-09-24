package com.wisso.wizefiles.core.entitlement.license

import com.wisso.wizefiles.core.entitlement.Entitlement
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class SignedLicenseDocument(
    val keyId: String,
    val payload: String,
    val signature: String,
) {
    fun serialize(): String =
        JSONObject()
            .put(KEY_KEY_ID, keyId)
            .put(KEY_PAYLOAD, payload)
            .put(KEY_SIGNATURE, signature)
            .toString()

    companion object {
        private const val KEY_KEY_ID = "key_id"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_SIGNATURE = "signature"
        private const val MAX_DOCUMENT_CHARS = 16 * 1024
        private const val MAX_KEY_ID_CHARS = 64

        fun parse(serialized: String): SignedLicenseDocument? {
            if (serialized.isBlank() || serialized.length > MAX_DOCUMENT_CHARS) return null
            return runCatching {
                val json = JSONObject(serialized)
                val keyId = json.getString(KEY_KEY_ID)
                val payload = json.getString(KEY_PAYLOAD)
                val signature = json.getString(KEY_SIGNATURE)
                if (keyId.isBlank() || keyId.length > MAX_KEY_ID_CHARS) return null
                if (payload.isBlank() || signature.isBlank()) return null
                SignedLicenseDocument(keyId, payload, signature)
            }.getOrNull()
        }
    }
}

data class LicenseClaims(
    val schemaVersion: Int,
    val licenseId: String,
    val packageName: String,
    val installationId: String,
    val entitlements: Set<Entitlement>,
    val issuedAtEpochMillis: Long,
    val notBeforeEpochMillis: Long,
    val refreshAfterEpochMillis: Long,
    val validUntilEpochMillis: Long,
    val serverTimeEpochMillis: Long,
)

internal object LicenseClaimsParser {
    private const val MAX_PAYLOAD_BYTES = 8 * 1024
    private const val MAX_IDENTIFIER_CHARS = 256

    fun decodePayload(payload: String): ByteArray? = runCatching {
        Base64.getUrlDecoder().decode(payload).takeIf { it.size <= MAX_PAYLOAD_BYTES }
    }.getOrNull()

    fun parse(payloadBytes: ByteArray): LicenseClaims? = runCatching {
        if (payloadBytes.isEmpty() || payloadBytes.size > MAX_PAYLOAD_BYTES) return null
        val json = JSONObject(payloadBytes.toString(Charsets.UTF_8))
        val entitlementsJson = json.getJSONArray("entitlements")
        val entitlements = buildSet {
            for (index in 0 until entitlementsJson.length()) {
                val name = entitlementsJson.getString(index)
                Entitlement.entries.firstOrNull { it.name == name }?.let(::add)
            }
        }
        LicenseClaims(
            schemaVersion = json.strictInt("schema_version"),
            licenseId = json.boundedString("license_id"),
            packageName = json.boundedString("package_name"),
            installationId = json.boundedString("installation_id"),
            entitlements = entitlements,
            issuedAtEpochMillis = json.strictLong("issued_at_ms"),
            notBeforeEpochMillis = json.strictLong("not_before_ms"),
            refreshAfterEpochMillis = json.strictLong("refresh_after_ms"),
            validUntilEpochMillis = json.strictLong("valid_until_ms"),
            serverTimeEpochMillis = json.strictLong("server_time_ms"),
        )
    }.getOrNull()

    private fun JSONObject.boundedString(name: String): String {
        val value = getString(name)
        require(value.isNotBlank() && value.length <= MAX_IDENTIFIER_CHARS)
        return value
    }

    private fun JSONObject.strictInt(name: String): Int {
        val value = get(name)
        require(value is Byte || value is Short || value is Int)
        return (value as Number).toInt()
    }

    private fun JSONObject.strictLong(name: String): Long {
        val value = get(name)
        require(value is Byte || value is Short || value is Int || value is Long)
        return (value as Number).toLong()
    }
}

internal fun licensePayloadJson(
    claims: LicenseClaims,
): JSONObject =
    JSONObject()
        .put("schema_version", claims.schemaVersion)
        .put("license_id", claims.licenseId)
        .put("package_name", claims.packageName)
        .put("installation_id", claims.installationId)
        .put("entitlements", JSONArray(claims.entitlements.map { it.name }))
        .put("issued_at_ms", claims.issuedAtEpochMillis)
        .put("not_before_ms", claims.notBeforeEpochMillis)
        .put("refresh_after_ms", claims.refreshAfterEpochMillis)
        .put("valid_until_ms", claims.validUntilEpochMillis)
        .put("server_time_ms", claims.serverTimeEpochMillis)
