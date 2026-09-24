// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

internal object SecretMigration {
    fun resolveStoredSecret(stored: String?, secretStore: SecretStore): String? {
        if (stored == null) {
            return null
        }
        if (SecretReferenceCodec.isReference(stored)) {
            return secretStore.getSecret(stored)
        }
        return stored
    }

    fun storeSecret(value: String, secretStore: SecretStore): String {
        val key = SecretReferenceCodec.createReference()
        secretStore.putSecret(key, value)
        return key
    }

    fun migrateLegacyPreferenceSecret(
        key: String,
        legacyValue: String?,
        defaultValue: String,
        secretStore: SecretStore,
        removeLegacyValue: (String) -> Unit
    ): String {
        secretStore.getSecret(key)?.let { return it }
        if (legacyValue != null) {
            secretStore.putSecret(key, legacyValue)
            removeLegacyValue(key)
            return legacyValue
        }
        return defaultValue
    }
}
