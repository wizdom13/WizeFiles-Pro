package com.wisso.wizefiles.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretMigrationTest {

    @Test
    fun `plaintext value resolves without creating secret entry`() {
        val store = InMemorySecretStore()

        val value = SecretMigration.resolveStoredSecret("legacy-password", store)

        assertEquals("legacy-password", value)
        assertTrue(store.data.isEmpty())
    }

    @Test
    fun `reference resolves to stored secret`() {
        val store = InMemorySecretStore()
        val reference = SecretReferenceCodec.createReference()
        store.putSecret(reference, "stored-secret")

        val value = SecretMigration.resolveStoredSecret(reference, store)

        assertEquals("stored-secret", value)
        assertEquals(1, store.data.size)
    }

    @Test
    fun `missing reference fails safely`() {
        val store = InMemorySecretStore()
        val missingReference = SecretReferenceCodec.createReference()

        val value = SecretMigration.resolveStoredSecret(missingReference, store)

        assertNull(value)
        assertTrue(store.data.isEmpty())
    }


    @Test
    fun `legacy plaintext can be rewritten into tracked secret references`() {
        val store = InMemorySecretStore()
        val legacyPassword = "legacy-password"

        val loadedValue = SecretMigration.resolveStoredSecret(legacyPassword, store)
        assertEquals(legacyPassword, loadedValue)
        assertTrue(store.data.isEmpty())

        val rewrittenReference = SecretMigration.storeSecret(loadedValue!!, store)
        val rewrittenSerialized = "owner-password=$rewrittenReference"

        assertTrue(SecretReferenceCodec.isReference(rewrittenReference))
        assertEquals(setOf(rewrittenReference), SecretReferenceCodec.findReferences(rewrittenSerialized))
        assertEquals(1, store.data.size)
        assertEquals(legacyPassword, store.getSecret(rewrittenReference))
    }

    @Test
    fun `store secret returns reference and supports read remove`() {
        val store = InMemorySecretStore()

        val reference = SecretMigration.storeSecret("write-value", store)

        assertTrue(SecretReferenceCodec.isReference(reference))
        assertEquals("write-value", store.getSecret(reference))

        store.removeSecret(reference)
        assertNull(store.getSecret(reference))
    }

    @Test
    fun `legacy shared preferences value is migrated and cleared`() {
        val store = InMemorySecretStore()
        var removedKey: String? = null

        val value = SecretMigration.migrateLegacyPreferenceSecret(
            key = "ftp_password",
            legacyValue = "legacy-cleartext",
            defaultValue = "default",
            secretStore = store,
            removeLegacyValue = { removedKey = it }
        )

        assertEquals("legacy-cleartext", value)
        assertEquals("legacy-cleartext", store.getSecret("ftp_password"))
        assertEquals("ftp_password", removedKey)
    }

    @Test
    fun `existing secure secret wins over legacy value`() {
        val store = InMemorySecretStore()
        store.putSecret("ftp_password", "secure")
        var removeCalled = false

        val value = SecretMigration.migrateLegacyPreferenceSecret(
            key = "ftp_password",
            legacyValue = "legacy-cleartext",
            defaultValue = "default",
            secretStore = store,
            removeLegacyValue = { removeCalled = true }
        )

        assertEquals("secure", value)
        assertFalse(removeCalled)
    }

    @Test
    fun `default value used when no secret exists`() {
        val store = InMemorySecretStore()

        val value = SecretMigration.migrateLegacyPreferenceSecret(
            key = "ftp_password",
            legacyValue = null,
            defaultValue = "default",
            secretStore = store,
            removeLegacyValue = { }
        )

        assertEquals("default", value)
        assertTrue(store.data.isEmpty())
    }

    private class InMemorySecretStore : SecretStore {
        val data = linkedMapOf<String, String?>()

        override fun getSecret(key: String): String? = data[key]

        override fun putSecret(key: String, value: String?) {
            data[key] = value
        }

        override fun removeSecret(key: String) {
            data.remove(key)
        }
    }
}
