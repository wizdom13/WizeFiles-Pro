package com.wisso.wizefiles.feature.apksigning

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.nio.file.Path
import java.util.UUID

class ProviderApksVerifier {
    fun verify(apks: Path): AndroidSplitSetVerificationReport {
        val directory = File(
            application.cacheDir,
            "apks-signing/verify-${UUID.randomUUID()}"
        ).apply { check(mkdirs()) }
        return try {
            val local = File(directory, "input.apks")
            copyToPrivateFile(apks, local)
            ApksArchiveSigningBackend(
                identityReader = AndroidPackageArchiveIdentityReader(application)
            ).verify(local, File(directory, "split-verification"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
