// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import java.util.Base64

internal object SecretReferenceReconciler {
    fun removeStaleReferences(
        oldSerializedValue: String?,
        newSerializedValue: String?,
        secretStoreProvider: () -> SecretStore
    ) {
        val oldReferences = extractReferencesFromSerializedParcel(oldSerializedValue)
        if (oldReferences.isEmpty()) {
            return
        }
        val newReferences = extractReferencesFromSerializedParcel(newSerializedValue)
        val staleReferences = oldReferences - newReferences
        if (staleReferences.isEmpty()) {
            return
        }
        val secretStore = secretStoreProvider()
        staleReferences.forEach { secretStore.removeSecret(it) }
    }

    private fun extractReferencesFromSerializedParcel(serializedValue: String?): Set<String> {
        if (serializedValue == null) {
            return emptySet()
        }
        val bytes = try {
            Base64.getDecoder().decode(serializedValue)
        } catch (_: IllegalArgumentException) {
            return emptySet()
        }
        if (bytes.isEmpty()) {
            return emptySet()
        }
        val asSingleByteChars = CharArray(bytes.size)
        bytes.forEachIndexed { index, byte ->
            asSingleByteChars[index] = (byte.toInt() and 0xFF).toChar()
        }
        val normalized = String(asSingleByteChars).replace("\u0000", "")
        return SecretReferenceCodec.findReferences(normalized)
    }
}
