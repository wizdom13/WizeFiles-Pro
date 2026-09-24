package com.wisso.wizefiles.feature.apksigning

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.nio.file.Path
import java.util.UUID

class ProviderAabVerifier(
    private val backend: AabSigningBackend = JarAabSigningBackend()
) {
    fun verify(aab: Path): AabVerificationReport {
        val directory = File(
            application.cacheDir,
            "aab-signing/verify-${UUID.randomUUID()}"
        ).apply { check(mkdirs()) }
        return try {
            val local = File(directory, "input.aab")
            copyToPrivateFile(aab, local)
            backend.verify(AabVerificationRequest(local))
        } finally {
            directory.deleteRecursively()
        }
    }
}
