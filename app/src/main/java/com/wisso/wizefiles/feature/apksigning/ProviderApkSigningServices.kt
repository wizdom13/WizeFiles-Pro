package com.wisso.wizefiles.feature.apksigning

import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.provider.common.createFile
import com.wisso.wizefiles.provider.common.deleteIfExists
import com.wisso.wizefiles.provider.common.moveTo
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.common.newOutputStream
import java.io.File
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

class ProviderApkVerifier(
    private val backend: ApkSigningBackend = ApksigApkSigningBackend()
) {
    fun verify(apk: Path, v4Signature: Path? = null, minSdkVersion: Int? = null): ApkVerificationReport {
        val directory = privateDirectory("verify")
        return try {
            val localApk = File(directory, "input.apk")
            copyToPrivateFile(apk, localApk)
            val localV4 = v4Signature?.let {
                File(directory, "input.apk.idsig").also { output -> copyToPrivateFile(it, output) }
            }
            backend.verify(ApkVerificationRequest(localApk, localV4, minSdkVersion))
        } finally {
            directory.deleteRecursively()
        }
    }
}

class ProviderApkKeyStoreService(
    private val keyStoreService: ApkSigningKeyStoreService = ApkSigningKeyStoreService()
) {
    fun aliases(
        source: Path,
        format: ApkKeyStoreFormat,
        secrets: ApkSigningSecrets
    ): List<ApkSigningKeyAlias> {
        val directory = privateDirectory("key-inspection")
        return try {
            val local = File(directory, "key-store")
            copyToPrivateFile(source, local)
            keyStoreService.aliases(local, format, secrets)
        } finally {
            directory.deleteRecursively()
        }
    }

    fun generatePkcs12(
        target: Path,
        request: ApkSigningKeyGenerationRequest,
        secrets: ApkSigningSecrets
    ): ApkSigningKeyAlias {
        val directory = privateDirectory("key")
        val local = File(directory, "generated.p12")
        return try {
            val result = keyStoreService.generatePkcs12(local, request, secrets)
            copyNewFile(local, target)
            result
        } finally {
            directory.deleteRecursively()
        }
    }
}

internal fun copyToPrivateFile(source: Path, target: File, progress: (Long) -> Unit = {}) {
    source.newInputStream().buffered().use { input ->
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                progress(count.toLong())
            }
        }
    }
}

internal fun copyNewFile(source: File, target: Path, progress: (Long) -> Unit = {}) {
    val temporary = target.resolveSibling(
        ".wizefiles-apk-signing-${UUID.randomUUID()}"
    )
    try {
        temporary.createFile()
        source.inputStream().buffered().use { input ->
            temporary.newOutputStream(
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    progress(count.toLong())
                }
            }
        }
        temporary.moveTo(target)
    } catch (exception: Exception) {
        runCatching { temporary.deleteIfExists() }
        throw exception
    }
}

private fun privateDirectory(purpose: String): File =
    File(application.cacheDir, "apk-signing/$purpose-${UUID.randomUUID()}").apply {
        check(mkdirs()) { "Unable to create private APK signing staging directory" }
    }
