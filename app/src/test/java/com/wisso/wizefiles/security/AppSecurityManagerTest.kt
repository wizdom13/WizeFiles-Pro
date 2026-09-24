// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import java.io.File
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSecurityManagerTest {

    @Test
    fun `set password stores derived value and verifies correctly`() {
        val secretStore = FakeSecretStore()
        val manager = createManager(secretStore = secretStore)

        assertTrue(manager.setPassword("pass123"))
        assertTrue(manager.verifyPassword("pass123"))
        assertFalse(manager.verifyPassword("wrong"))

        val stored = secretStore.values[KEYS.passwordSecret]
        assertTrue(stored?.startsWith("wzfsec_v1:") == true)
        assertFalse(stored?.contains("pass123") == true)
    }

    @Test
    fun `versioned password record verifies correctly`() {
        val secretStore = FakeSecretStore()
        val manager = createManager(secretStore = secretStore)
        manager.setPassword("pass123")

        assertTrue(manager.verifyPassword("pass123"))
        assertFalse(manager.verifyPassword("wrong"))
    }

    @Test
    fun `malformed versioned password record is rejected`() {
        val secretStore = FakeSecretStore().apply {
            putSecret(KEYS.passwordSecret, "wzfsec_v2:AA:AA")
        }
        val manager = createManager(secretStore = secretStore)

        assertFalse(manager.hasPassword())
        assertFalse(manager.verifyPassword("pass123"))
    }

    @Test
    fun `empty password is rejected`() {
        val manager = createManager()

        assertFalse(manager.setPassword(""))
        assertFalse(manager.hasPassword())
    }

    @Test
    fun `malformed stored password record is treated as no password`() {
        val secretStore = FakeSecretStore().apply {
            putSecret(KEYS.passwordSecret, "malformed_record")
        }
        val manager = createManager(secretStore = secretStore)

        assertFalse(manager.hasPassword())
    }

    @Test
    fun `decodable but invalid length password record is treated as no password`() {
        val secretStore = FakeSecretStore().apply {
            putSecret(KEYS.passwordSecret, "AA:AA")
        }
        val manager = createManager(secretStore = secretStore)

        assertFalse(manager.hasPassword())
    }

    @Test
    fun `legacy valid password record is accepted and migrated after verification`() {
        val secretStore = FakeSecretStore()
        val manager = createManager(secretStore = secretStore)
        manager.setPassword("pass123")
        val versioned = secretStore.values[KEYS.passwordSecret]!!
        val legacy = versioned.substringAfter("wzfsec_v1:")
        secretStore.putSecret(KEYS.passwordSecret, legacy)

        assertTrue(manager.hasPassword())
        assertTrue(manager.verifyPassword("pass123"))
        assertTrue(secretStore.values[KEYS.passwordSecret]?.startsWith("wzfsec_v1:") == true)
    }

    @Test
    fun `setting a new password replaces old value`() {
        val secretStore = FakeSecretStore()
        val manager = createManager(secretStore = secretStore)

        manager.setPassword("oldpass")
        val oldValue = secretStore.values[KEYS.passwordSecret]

        manager.setPassword("newpass")
        val newValue = secretStore.values[KEYS.passwordSecret]

        assertNotEquals(oldValue, newValue)
        assertFalse(manager.verifyPassword("oldpass"))
        assertTrue(manager.verifyPassword("newpass"))
    }

    @Test
    fun `immediate relock allows one protected access then relocks`() {
        val prefs = FakePreferenceStore(
            mutableMapOf(
                KEYS.protectBrowser to true,
                KEYS.relockTimeout to "0"
            )
        )
        val manager = createManager(preferenceStore = prefs)
        manager.setPassword("pass123")

        assertTrue(manager.requiresUnlock(ProtectedTarget.BROWSER))

        manager.markUnlocked()
        assertFalse(manager.requiresUnlock(ProtectedTarget.BROWSER))
        assertTrue(manager.requiresUnlock(ProtectedTarget.BROWSER))
    }

    @Test
    fun `timeout relock keeps browser unlocked until timeout`() {
        var now = 1_000L
        val prefs = FakePreferenceStore(
            mutableMapOf(
                KEYS.protectBrowser to true,
                KEYS.relockTimeout to "60000"
            )
        )
        val manager = createManager(preferenceStore = prefs, nowMillis = { now })
        manager.setPassword("pass123")

        assertTrue(manager.requiresUnlock(ProtectedTarget.BROWSER))
        manager.markUnlocked()
        assertFalse(manager.requiresUnlock(ProtectedTarget.BROWSER))
        assertFalse(manager.requiresUnlock(ProtectedTarget.BROWSER))

        now += 60_001L
        assertTrue(manager.requiresUnlock(ProtectedTarget.BROWSER))
    }

    @Test
    fun `protection toggle off keeps target unprotected`() {
        val prefs = FakePreferenceStore(
            mutableMapOf(
                KEYS.protectBrowser to false,
                KEYS.relockTimeout to "60000"
            )
        )
        val manager = createManager(preferenceStore = prefs)
        manager.setPassword("pass123")

        assertFalse(manager.requiresUnlock(ProtectedTarget.BROWSER))
    }

    @Test
    fun `app security manager uses project base64 utility instead of java util base64`() {
        val source = File("src/main/java/com/wisso/wizefiles/core/security/AppSecurityManager.kt").readText()

        assertFalse(source.contains("import java.util.Base64"))
        assertTrue(source.contains("toBase64()"))
        assertTrue(source.contains("asBase64()"))
    }

    private fun createManager(
        preferenceStore: FakePreferenceStore = FakePreferenceStore(
            mutableMapOf(
                KEYS.protectBrowser to true,
                KEYS.enableBiometric to false,
                KEYS.relockTimeout to AppSecurityManager.DEFAULT_RELOCK_TIMEOUT_VALUE
            )
        ),
        secretStore: FakeSecretStore = FakeSecretStore(),
        nowMillis: () -> Long = { 0L }
    ): AppSecurityManager = AppSecurityManager(
        preferenceStore = preferenceStore,
        secretStore = secretStore,
        keys = KEYS,
        nowMillis = nowMillis,
        encodeBase64 = { Base64.getEncoder().withoutPadding().encodeToString(it) },
        decodeBase64 = { Base64.getDecoder().decode(it) }
    )

    private class FakePreferenceStore(
        private val values: MutableMap<String, Any>
    ) : AppSecurityManager.PreferenceStore {
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue

        override fun getString(key: String, defaultValue: String?): String? =
            values[key] as? String ?: defaultValue
    }

    private class FakeSecretStore : SecretStore {
        val values = mutableMapOf<String, String?>()

        override fun getSecret(key: String): String? = values[key]

        override fun putSecret(key: String, value: String?) {
            values[key] = value
        }

        override fun removeSecret(key: String) {
            values.remove(key)
        }
    }

    companion object {
        private val KEYS = AppSecurityManager.Keys(
            passwordSecret = "security_password",
            protectBrowser = "protect_browser",
            enableBiometric = "enable_biometric",
            relockTimeout = "relock_timeout"
        )
    }
}
