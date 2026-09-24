// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import android.content.Context
import android.content.SharedPreferences
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.asBase64
import com.wisso.wizefiles.util.toBase64
import com.wisso.wizefiles.util.toByteArray
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AppSecurityManager(
    private val preferenceStore: PreferenceStore,
    private val secretStore: SecretStore,
    private val keys: Keys,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val secureRandom: SecureRandom = SecureRandom(),
    private val encodeBase64: (ByteArray) -> String = { it.toBase64().value },
    private val decodeBase64: (String) -> ByteArray = { it.asBase64().toByteArray() }
) {

    private var unlockedAtMillis: Long? = null
    private var immediateAccessPending: Boolean = false

    fun hasPassword(): Boolean = loadPasswordRecord() != null

    fun setPassword(password: String): Boolean {
        if (password.isBlank()) {
            return false
        }
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val hash = deriveHash(password, salt)
        secretStore.putSecret(keys.passwordSecret, serializePasswordRecord(PasswordRecord(salt, hash)))
        clearSession()
        return true
    }

    fun verifyPassword(password: String): Boolean {
        val stored = secretStore.getSecret(keys.passwordSecret) ?: return false
        val parsed = deserializePasswordRecord(stored) ?: return false
        val record = parsed.record
        val derived = deriveHash(password, record.salt)
        val isValid = record.hash.contentEquals(derived)
        if (isValid && parsed.isLegacy) {
            secretStore.putSecret(keys.passwordSecret, serializePasswordRecord(record))
        }
        return isValid
    }

    fun shouldProtectBrowser(): Boolean = preferenceStore.getBoolean(keys.protectBrowser, false)

    fun isBiometricEnabled(): Boolean = preferenceStore.getBoolean(keys.enableBiometric, false)

    fun relockTimeoutMillis(): Long =
        preferenceStore.getString(keys.relockTimeout, DEFAULT_RELOCK_TIMEOUT_VALUE)
            ?.toLongOrNull()
            ?: DEFAULT_RELOCK_TIMEOUT_VALUE.toLong()

    fun requiresUnlock(target: ProtectedTarget): Boolean {
        if (!isProtectionEnabled(target)) {
            return false
        }
        if (!hasPassword()) {
            return false
        }
        val timeoutMillis = relockTimeoutMillis()
        if (timeoutMillis <= 0L) {
            return if (immediateAccessPending) {
                immediateAccessPending = false
                false
            } else {
                true
            }
        }
        val unlockedAt = unlockedAtMillis ?: return true
        return if (nowMillis() - unlockedAt >= timeoutMillis) {
            unlockedAtMillis = null
            true
        } else {
            false
        }
    }

    fun markUnlocked() {
        if (relockTimeoutMillis() <= 0L) {
            immediateAccessPending = true
            unlockedAtMillis = null
        } else {
            unlockedAtMillis = nowMillis()
            immediateAccessPending = false
        }
    }

    fun clearSession() {
        unlockedAtMillis = null
        immediateAccessPending = false
    }

    private fun isProtectionEnabled(target: ProtectedTarget): Boolean = when (target) {
        ProtectedTarget.BROWSER -> shouldProtectBrowser()
    }

    private fun loadPasswordRecord(): PasswordRecord? =
        secretStore.getSecret(keys.passwordSecret)?.let { parsePasswordRecordWithMetadata(it)?.record }

    private fun deriveHash(password: String, salt: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_LENGTH_BITS)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(keySpec).encoded
    }

    data class Keys(
        val passwordSecret: String,
        val protectBrowser: String,
        val enableBiometric: String,
        val relockTimeout: String
    )

    interface PreferenceStore {
        fun getBoolean(key: String, defaultValue: Boolean): Boolean
        fun getString(key: String, defaultValue: String?): String?
    }

    private data class PasswordRecord(val salt: ByteArray, val hash: ByteArray)

    private fun serializePasswordRecord(record: PasswordRecord): String = listOf(
        PASSWORD_RECORD_VERSION_V1,
        encodeBase64(record.salt),
        encodeBase64(record.hash)
    ).joinToString(separator = PASSWORD_RECORD_SEPARATOR)

    private fun parsePasswordRecordWithMetadata(value: String): ParsedPasswordRecord? {
        val parts = value.split(PASSWORD_RECORD_SEPARATOR)
        return when {
            parts.size == LEGACY_PASSWORD_RECORD_PARTS -> {
                parsePasswordRecordParts(parts[0], parts[1], isLegacy = true)
            }
            parts.size == VERSIONED_PASSWORD_RECORD_PARTS && parts[0] == PASSWORD_RECORD_VERSION_V1 -> {
                parsePasswordRecordParts(parts[1], parts[2], isLegacy = false)
            }
            else -> null
        }
    }

    private fun parsePasswordRecordParts(
        saltPart: String,
        hashPart: String,
        isLegacy: Boolean = false
    ): ParsedPasswordRecord? = runCatching {
        val salt = decodeBase64(saltPart)
        val hash = decodeBase64(hashPart)
        if (salt.size != SALT_LENGTH_BYTES || hash.size != HASH_LENGTH_BITS / 8) {
            throw IllegalArgumentException("Invalid password record")
        }
        ParsedPasswordRecord(PasswordRecord(salt, hash), isLegacy)
    }.getOrNull()

    private data class ParsedPasswordRecord(
        val record: PasswordRecord,
        val isLegacy: Boolean
    )

    private fun deserializePasswordRecord(value: String): ParsedPasswordRecord? =
        parsePasswordRecordWithMetadata(value)

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val SALT_LENGTH_BYTES = 16
        private const val HASH_LENGTH_BITS = 256
        private const val PASSWORD_RECORD_SEPARATOR = ":"
        private const val PASSWORD_RECORD_VERSION_V1 = "wzfsec_v1"
        private const val LEGACY_PASSWORD_RECORD_PARTS = 2
        private const val VERSIONED_PASSWORD_RECORD_PARTS = 3
        const val DEFAULT_RELOCK_TIMEOUT_VALUE = "0"

        fun create(
            context: Context,
            sharedPreferences: SharedPreferences,
            secretStore: SecretStore
        ): AppSecurityManager {
            val keys = Keys(
                passwordSecret = context.getString(R.string.pref_key_security_password),
                protectBrowser = context.getString(R.string.pref_key_protect_browser),
                enableBiometric = context.getString(R.string.pref_key_enable_biometric),
                relockTimeout = context.getString(R.string.pref_key_time_to_relock)
            )
            val preferenceStore = object : PreferenceStore {
                override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
                    sharedPreferences.getBoolean(key, defaultValue)

                override fun getString(key: String, defaultValue: String?): String? =
                    sharedPreferences.getString(key, defaultValue)
            }
            return AppSecurityManager(preferenceStore, secretStore, keys)
        }
    }
}

enum class ProtectedTarget {
    BROWSER
}
