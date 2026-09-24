// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.packageinstaller

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class PackageInstallerSessionBackend(private val context: Context) {
    private val installer = context.packageManager.packageInstaller

    fun install(
        plan: PackageInstallPlan,
        options: PackageInstallOptions,
        statusReceiver: IntentSender,
        onSessionCreated: (Int) -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Int {
        require(plan.comparison.signerCompatible) {
            "The incoming package cannot update the installed signing identity"
        }
        if (plan.comparison.action == PackageInstallAction.DOWNGRADE && !options.allowDowngrade) {
            throw IOException("Downgrade was not explicitly approved")
        }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                setAppPackageName(plan.packageName)
                setSize(plan.totalApkBytes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setPackageSource(options.packageSource)
                }
                options.originatingUri?.let { setOriginatingUri(Uri.parse(it)) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    options.requestUpdateOwnership &&
                    context.checkSelfPermission(Manifest.permission.ENFORCE_UPDATE_OWNERSHIP) ==
                    PackageManager.PERMISSION_GRANTED) {
                    setRequestUpdateOwnership(true)
                }
            }
        val sessionId = installer.createSession(params)
        onSessionCreated(sessionId)
        try {
            installer.openSession(sessionId).use { session ->
                var written = 0L
                plan.apks.forEachIndexed { index, apk ->
                    val file = apk.stagedFile ?: throw IOException("An APK is not staged")
                    verifyStagedFile(file, apk.sizeBytes, apk.sha256)
                    val name = if (apk.isBase) "base.apk" else {
                        val safeSplit = apk.splitName.orEmpty()
                            .replace(Regex("[^A-Za-z0-9._-]"), "_")
                            .take(120)
                            .ifBlank { index.toString() }
                        "split-$safeSplit.apk"
                    }
                    session.openWrite(name, 0, file.length()).use { output ->
                        file.inputStream().buffered().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                written += count
                                onProgress(written, plan.totalApkBytes)
                            }
                        }
                        session.fsync(output)
                    }
                }
                session.commit(statusReceiver)
            }
            return sessionId
        } catch (exception: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw exception
        }
    }

    fun abandon(sessionId: Int) {
        installer.abandonSession(sessionId)
    }

    private fun verifyStagedFile(file: File, expectedSize: Long, expectedSha256: String) {
        if (!file.isFile || file.length() != expectedSize) {
            throw IOException("A staged APK changed before installation")
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
            throw IOException("A staged APK failed its final integrity check")
        }
    }
}
