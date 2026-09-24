package com.wisso.wizefiles.feature.packageinstaller

import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class ObbAccessException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ObbInstallCoordinator(
    private val externalStorageRoot: File = Environment.getExternalStorageDirectory()
) {
    fun install(plan: PackageInstallPlan, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        if (plan.expansions.isEmpty()) return
        require(PACKAGE_NAME.matches(plan.packageName)) { "Invalid expansion package name" }
        val targetDirectory = File(externalStorageRoot, "Android/obb/${plan.packageName}")
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw ObbAccessException(
                "Android does not allow WizeFiles to create the app's OBB directory on this device"
            )
        }
        val transaction = UUID.randomUUID().toString()
        val temporaryFiles = linkedMapOf<PackageExpansion, File>()
        val backups = linkedMapOf<File, File>()
        var copied = 0L
        try {
            plan.expansions.forEach { expansion ->
                validateExpansion(plan, expansion)
                val source = expansion.stagedFile ?: throw IOException("Expansion is not staged")
                verify(source, expansion.sizeBytes, expansion.sha256)
                val temporary = File(targetDirectory, ".${expansion.fileName}.$transaction.part")
                FileOutputStream(temporary).use { output ->
                    source.inputStream().buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            onProgress(copied, plan.totalExpansionBytes)
                        }
                    }
                    output.fd.sync()
                }
                verify(temporary, expansion.sizeBytes, expansion.sha256)
                temporaryFiles[expansion] = temporary
            }
            plan.expansions.forEach { expansion ->
                val target = File(targetDirectory, expansion.fileName)
                if (target.exists()) {
                    val backup = File(targetDirectory, ".${expansion.fileName}.$transaction.bak")
                    if (!target.renameTo(backup)) throw IOException(
                        "Unable to preserve the existing expansion file"
                    )
                    backups[target] = backup
                }
                val temporary = temporaryFiles.getValue(expansion)
                if (!temporary.renameTo(target)) throw IOException(
                    "Unable to commit the expansion file"
                )
            }
            backups.values.forEach(File::delete)
        } catch (exception: Exception) {
            plan.expansions.forEach { expansion ->
                val target = File(targetDirectory, expansion.fileName)
                val backup = backups[target]
                if (backup != null) {
                    target.delete()
                    backup.renameTo(target)
                } else if (temporaryFiles[expansion]?.exists() == false) {
                    target.delete()
                }
            }
            throw if (exception is ObbAccessException) exception else ObbAccessException(
                "The app was installed, but its expansion files could not be installed",
                exception
            )
        } finally {
            temporaryFiles.values.forEach(File::delete)
        }
    }

    private fun validateExpansion(plan: PackageInstallPlan, expansion: PackageExpansion) {
        require(expansion.fileName == expansion.fileName.substringAfterLast('/')) {
            "Expansion target must be a file name"
        }
        val expected = Regex(
            "^(main|patch)\\.${plan.versionCode}\\.${Regex.escape(plan.packageName)}\\.obb$"
        )
        require(expected.matches(expansion.fileName)) {
            "Expansion file does not match the installed package"
        }
    }

    private fun verify(file: File, expectedSize: Long, expectedSha256: String) {
        if (!file.isFile || file.length() != expectedSize) {
            throw IOException("Expansion file size changed")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        if (!actual.equals(expectedSha256, true)) throw IOException(
            "Expansion file failed its integrity check"
        )
    }

    private companion object {
        val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
    }
}
