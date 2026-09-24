package com.wisso.wizefiles.feature.apksigning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSigningSecretsTest {
    @Test
    fun `secrets copy caller arrays redact diagnostics and clear deterministically`() {
        val caller = "store-secret".toCharArray()
        val secrets = ApkSigningSecrets(caller)
        caller.fill('x')

        val recovered = secrets.storePasswordCopy()
        assertEquals("store-secret", String(recovered))
        recovered.fill('\u0000')
        assertTrue("[REDACTED]" in secrets.toString())

        secrets.close()
        assertThrows(IllegalStateException::class.java) { secrets.storePasswordCopy() }
    }

    @Test
    fun `registry is one use and owns a separate copy`() {
        val original = ApkSigningSecrets("password".toCharArray())
        ApkSigningSecretRegistry.put("operation", original)
        original.close()

        assertTrue(ApkSigningSecretRegistry.has("operation"))
        val taken = requireNotNull(ApkSigningSecretRegistry.take("operation"))
        assertEquals("password", String(taken.storePasswordCopy()))
        taken.close()
        assertTrue(!ApkSigningSecretRegistry.has("operation"))
    }
}
