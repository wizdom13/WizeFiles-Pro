package com.wisso.wizefiles.feature.packageinstaller

import com.topjohnwu.superuser.Shell
import com.wisso.wizefiles.provider.root.LibSuFileServiceLauncher
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Root-only package installer. Sui is intentionally not treated as a shell command bridge. */
class PrivilegedPackageInstallerBackend {
    fun isAvailable(): Boolean = runCatching {
        LibSuFileServiceLauncher.isSuAvailable()
    }.getOrDefault(false)

    fun install(
        plan: PackageInstallPlan,
        options: PackageInstallOptions,
        onSessionCreated: (Int) -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Int {
        require(isAvailable()) { "Root package installation is not available" }
        require(options.usePrivilegedInstaller) {
            "Privileged package installation was not explicitly selected"
        }
        require(plan.comparison.signerCompatible || options.allowSignatureMismatch) {
            "The incoming package has a different signing identity and bypass was not approved"
        }
        if (plan.comparison.action == PackageInstallAction.DOWNGRADE && !options.allowDowngrade) {
            throw IOException("Downgrade was not explicitly approved")
        }
        plan.apks.forEach { apk ->
            val file = apk.stagedFile ?: throw IOException("An APK is not staged")
            verify(file, apk.sizeBytes, apk.sha256)
        }
        val created = command(
            PrivilegedInstallCommands.create(
                plan.totalApkBytes,
                options.allowDowngrade,
                options.targetUserId
            )
        )
        val sessionId = PrivilegedInstallCommands.parseSessionId(created)
            ?: throw IOException("Android did not return a privileged install session ID")
        onSessionCreated(sessionId)
        var written = 0L
        try {
            plan.apks.forEachIndexed { index, apk ->
                val file = requireNotNull(apk.stagedFile)
                val name = if (apk.isBase) "base.apk" else "split-$index.apk"
                command(PrivilegedInstallCommands.write(sessionId, name, apk.sizeBytes, file.path))
                written += apk.sizeBytes
                onProgress(written, plan.totalApkBytes)
            }
            command(PrivilegedInstallCommands.commit(sessionId))
            return sessionId
        } catch (exception: Exception) {
            runCatching { command(PrivilegedInstallCommands.abandon(sessionId)) }
            throw exception
        }
    }

    fun abandon(sessionId: Int) {
        command(PrivilegedInstallCommands.abandon(sessionId))
    }

    private fun command(command: String): String {
        val result = Shell.cmd(command).exec()
        val output = result.out.joinToString("\n").trim()
        if (!result.isSuccess || output.startsWith("Failure", ignoreCase = true)) {
            val detail = (result.err + result.out).joinToString("\n")
                .trim().take(1000)
            throw IOException(detail.ifBlank { "Privileged package installer command failed" })
        }
        return output
    }

    private fun verify(file: File, expectedSize: Long, expectedSha256: String) {
        if (!file.isFile || file.length() != expectedSize) {
            throw IOException("A staged APK changed before privileged installation")
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
            "A staged APK failed its final privileged-install integrity check"
        )
    }
}
