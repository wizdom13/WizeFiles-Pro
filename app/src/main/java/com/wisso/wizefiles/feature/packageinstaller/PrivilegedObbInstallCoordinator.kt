package com.wisso.wizefiles.feature.packageinstaller

import android.os.Environment
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

internal class PrivilegedObbInstallCoordinator(
    private val externalStorageRoot: File = Environment.getExternalStorageDirectory()
) {
    fun install(plan: PackageInstallPlan, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        if (plan.expansions.isEmpty()) return
        require(PrivilegedPackageInstallerBackend().isAvailable()) {
            "Root expansion installation is not available"
        }
        require(PACKAGE_NAME.matches(plan.packageName)) { "Invalid expansion package name" }
        val directory = File(externalStorageRoot, "Android/obb/${plan.packageName}").path
        command("mkdir -p -- ${shellQuote(directory)}")
        val transaction = UUID.randomUUID().toString()
        val temporary = linkedMapOf<PackageExpansion, String>()
        val backups = linkedMapOf<String, String>()
        val committed = linkedSetOf<String>()
        var copied = 0L
        try {
            plan.expansions.forEach { expansion ->
                validateExpansion(plan, expansion)
                val source = expansion.stagedFile ?: throw IOException("Expansion is not staged")
                verify(source, expansion.sizeBytes, expansion.sha256)
                val part = "$directory/.${expansion.fileName}.$transaction.part"
                command("cp -- ${shellQuote(source.path)} ${shellQuote(part)}")
                command("chmod 0644 -- ${shellQuote(part)}")
                command("sync")
                verifyPrivileged(part, expansion.sizeBytes, expansion.sha256)
                temporary[expansion] = part
                copied += expansion.sizeBytes
                onProgress(copied, plan.totalExpansionBytes)
            }
            plan.expansions.forEach { expansion ->
                val target = "$directory/${expansion.fileName}"
                if (exists(target)) {
                    val backup = "$directory/.${expansion.fileName}.$transaction.bak"
                    command("mv -- ${shellQuote(target)} ${shellQuote(backup)}")
                    backups[target] = backup
                }
                command("mv -- ${shellQuote(temporary.getValue(expansion))} ${shellQuote(target)}")
                committed += target
            }
            backups.values.forEach { command("rm -f -- ${shellQuote(it)}") }
        } catch (exception: Exception) {
            (committed + backups.keys).forEach { target ->
                val backup = backups[target]
                if (backup == null) {
                    runCatching { command("rm -f -- ${shellQuote(target)}") }
                } else {
                    runCatching {
                        command("rm -f -- ${shellQuote(target)}")
                        command("mv -- ${shellQuote(backup)} ${shellQuote(target)}")
                    }
                }
            }
            throw ObbAccessException(
                "The app was installed, but root could not install its expansion files",
                exception
            )
        } finally {
            temporary.values.forEach { runCatching { command("rm -f -- ${shellQuote(it)}") } }
        }
    }

    private fun validateExpansion(plan: PackageInstallPlan, expansion: PackageExpansion) {
        require(expansion.fileName == expansion.fileName.substringAfterLast('/'))
        require(
            Regex(
                "^(main|patch)\\.${plan.versionCode}\\.${Regex.escape(plan.packageName)}\\.obb$"
            ).matches(expansion.fileName)
        ) { "Expansion file does not match the installed package" }
    }

    private fun verifyPrivileged(path: String, expectedSize: Long, expectedSha256: String) {
        val size = command("stat -c %s -- ${shellQuote(path)}").lineSequence().firstOrNull()
            ?.trim()?.toLongOrNull()
        if (size != expectedSize) throw IOException("Privileged expansion copy changed size")
        val digest = command("sha256sum -- ${shellQuote(path)}")
            .trim().substringBefore(' ')
        if (!digest.equals(expectedSha256, ignoreCase = true)) {
            throw IOException("Privileged expansion copy failed its integrity check")
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
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw IOException("Expansion file failed its integrity check")
        }
    }

    private fun exists(path: String): Boolean =
        Shell.cmd("test -e -- ${shellQuote(path)}").exec().isSuccess

    private fun command(value: String): String {
        val result = Shell.cmd(value).exec()
        val output = result.out.joinToString("\n").trim()
        if (!result.isSuccess) {
            val detail = (result.err + result.out).joinToString("\n").trim().take(1000)
            throw IOException(detail.ifBlank { "Privileged expansion command failed" })
        }
        return output
    }

    private companion object {
        val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
    }
}
