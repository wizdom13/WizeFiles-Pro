package com.wisso.wizefiles.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SecretReferenceReconcilerTest {

    @Test
    fun `updating secret-bearing object removes previous secret ref`() {
        val store = InMemorySecretStore()
        val oldReference = SecretMigration.storeSecret("old", store)
        val newReference = SecretMigration.storeSecret("new", store)

        SecretReferenceReconciler.removeStaleReferences(
            oldSerializedValue = encodeParcelLikePayload(oldReference),
            newSerializedValue = encodeParcelLikePayload(newReference),
            secretStoreProvider = { store }
        )

        assertNull(store.getSecret(oldReference))
        assertEquals("new", store.getSecret(newReference))
    }

    @Test
    fun `deleting object removes all associated secret refs`() {
        val store = InMemorySecretStore()
        val passwordReference = SecretMigration.storeSecret("password", store)
        val tokenReference = SecretMigration.storeSecret("token", store)

        SecretReferenceReconciler.removeStaleReferences(
            oldSerializedValue = encodeParcelLikePayload(passwordReference, tokenReference),
            newSerializedValue = null,
            secretStoreProvider = { store }
        )

        assertTrue(store.data.isEmpty())
    }

    @Test
    fun `clearing secret field removes old secret ref`() {
        val store = InMemorySecretStore()
        val oldReference = SecretMigration.storeSecret("private-key-password", store)

        SecretReferenceReconciler.removeStaleReferences(
            oldSerializedValue = encodeParcelLikePayload(oldReference),
            newSerializedValue = encodeParcelLikePayload("field-cleared"),
            secretStoreProvider = { store }
        )

        assertNull(store.getSecret(oldReference))
    }

    @Test
    fun `missing secret refs fail safely`() {
        val store = InMemorySecretStore()
        val missingReference = SecretReferenceCodec.createReference()

        SecretReferenceReconciler.removeStaleReferences(
            oldSerializedValue = encodeParcelLikePayload(missingReference),
            newSerializedValue = null,
            secretStoreProvider = { store }
        )

        assertFalse(store.data.containsKey(missingReference))
        assertTrue(store.removedKeys.contains(missingReference))
    }

    @Test
    fun `non secret parcel payload does not initialize secret store`() {
        var providerInvoked = false

        SecretReferenceReconciler.removeStaleReferences(
            oldSerializedValue = encodeParcelLikePayload("name", "sort", "ascending"),
            newSerializedValue = encodeParcelLikePayload("name", "sort", "descending"),
            secretStoreProvider = {
                providerInvoked = true
                error("secret store should not be needed when no secret references exist")
            }
        )

        assertFalse(providerInvoked)
    }

    private fun encodeParcelLikePayload(vararg values: String): String {
        val payload = values.joinToString(separator = "|")
        return Base64.getEncoder().encodeToString(payload.toByteArray())
    }

    private class InMemorySecretStore : SecretStore {
        val data = linkedMapOf<String, String?>()
        val removedKeys = mutableListOf<String>()

        override fun getSecret(key: String): String? = data[key]

        override fun putSecret(key: String, value: String?) {
            data[key] = value
        }

        override fun removeSecret(key: String) {
            removedKeys += key
            data.remove(key)
        }
    }
}
