package com.wisso.wizefiles.feature.apksigning

import java.util.concurrent.ConcurrentHashMap

class ApkSigningSecrets(
    storePassword: CharArray,
    keyPassword: CharArray? = null
) : AutoCloseable {
    private var storePassword = storePassword.copyOf()
    private var keyPassword = keyPassword?.copyOf()
    private var closed = false

    internal fun storePasswordCopy(): CharArray {
        check(!closed) { "Signing secrets have been cleared" }
        return storePassword.copyOf()
    }

    internal fun keyPasswordCopy(): CharArray {
        check(!closed) { "Signing secrets have been cleared" }
        return (keyPassword ?: storePassword).copyOf()
    }

    internal fun duplicate(): ApkSigningSecrets {
        val storeCopy = storePasswordCopy()
        val keyCopy = keyPasswordCopy()
        return try {
            ApkSigningSecrets(storeCopy, keyCopy)
        } finally {
            storeCopy.fill('\u0000')
            keyCopy.fill('\u0000')
        }
    }

    override fun close() {
        if (closed) return
        storePassword.fill('\u0000')
        keyPassword?.fill('\u0000')
        storePassword = CharArray(0)
        keyPassword = null
        closed = true
    }

    override fun toString(): String = "ApkSigningSecrets([REDACTED])"
}

internal object ApkSigningSecretRegistry {
    private val secrets = ConcurrentHashMap<String, ApkSigningSecrets>()

    fun put(operationId: String, value: ApkSigningSecrets) {
        require(operationId.isNotBlank())
        secrets.put(operationId, value.duplicate())?.close()
    }

    fun has(operationId: String): Boolean = secrets.containsKey(operationId)

    fun take(operationId: String): ApkSigningSecrets? = secrets.remove(operationId)

    fun clear(operationId: String) {
        secrets.remove(operationId)?.close()
    }
}
