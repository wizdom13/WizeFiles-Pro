package com.wisso.wizefiles.feature.apksigning

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.nio.file.Path
import java.util.UUID

class ProviderXapkVerifier {
    fun verify(xapk: Path): AndroidSplitSetVerificationReport {
        val directory = File(
            application.cacheDir,
            "xapk-signing/verify-${UUID.randomUUID()}"
        ).apply { check(mkdirs()) }
        return try {
            val local = File(directory, "input.xapk")
            copyToPrivateFile(xapk, local)
            XapkArchiveSigningBackend(
                identityReader = AndroidPackageArchiveIdentityReader(application)
            ).verify(local, File(directory, "package-verification"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
