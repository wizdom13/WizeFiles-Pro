package com.wisso.wizefiles.feature.apksigning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSigningWorkflowPersistenceContractTest {
    @Test
    fun `durable metadata round trips without any signing secret field`() {
        val spec = ApkSigningWorkflowSpec(
            operationId = "operation",
            sourceUri = "content://provider/input.apk",
            outputUri = "content://provider/output.apk",
            keyStoreUri = "content://provider/key.p12",
            keyAlias = "release"
        )

        val encoded = ApkSigningOperationCodec.encode(spec)
        val root = JSONObject(encoded)

        assertEquals(spec, ApkSigningOperationCodec.decode(encoded))
        assertEquals(
            setOf("operationId", "sourceUri", "outputUri", "keyStoreUri", "keyAlias",
                "keyStoreFormat", "schemes", "minSdkVersion", "conflictPolicy"),
            root.keys().asSequence().toSet()
        )
        assertFalse(root.keys().asSequence().any { it.contains("password", ignoreCase = true) })
    }

    @Test
    fun `secrets are one use and recovery without them remains explicit`() {
        ApkSigningSecretRegistry.clear("recovery")
        assertNull(ApkSigningSecretRegistry.take("recovery"))

        ApkSigningSecretRegistry.put("recovery", ApkSigningSecrets("ephemeral".toCharArray()))
        val secrets = ApkSigningSecretRegistry.take("recovery")
        assertTrue(secrets != null)
        assertNull(ApkSigningSecretRegistry.take("recovery"))
        secrets?.close()
    }
}
