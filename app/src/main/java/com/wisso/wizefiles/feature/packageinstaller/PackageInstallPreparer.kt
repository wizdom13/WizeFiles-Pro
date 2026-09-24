// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.packageinstaller

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.wisso.wizefiles.feature.apksigning.AndroidPackageArchiveIdentityReader
import com.wisso.wizefiles.feature.apksigning.AndroidPackageContainerFormat
import com.wisso.wizefiles.feature.apksigning.AndroidPackageContainerInspector
import com.wisso.wizefiles.feature.apksigning.AndroidSplitSetVerifier
import com.wisso.wizefiles.feature.apksigning.ApkVerificationRequest
import com.wisso.wizefiles.feature.apksigning.ApksigApkSigningBackend
import com.wisso.wizefiles.feature.apksigning.normalizeCertificateDigests
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import org.json.JSONObject

class PackageInstallPreparer(private val context: Context) {
    private val packageManager = context.packageManager
    private val signatureBackend = ApksigApkSigningBackend()
    private val identityReader = AndroidPackageArchiveIdentityReader(context)
    private val inspector = AndroidPackageContainerInspector()

    fun prepare(
        sourceFile: File,
        displayName: String,
        operationDirectory: File,
        operationId: String = UUID.randomUUID().toString()
    ): PackageInstallPlan {
        require(sourceFile.isFile && sourceFile.canRead()) { "Package source is not readable" }
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val kind = when (extension) {
            "apk" -> PackageArchiveKind.APK
            "apks" -> PackageArchiveKind.APKS
            "apkm" -> PackageArchiveKind.APKM
            "xapk" -> PackageArchiveKind.XAPK
            else -> throw IOException("Unsupported Android package format")
        }
        val prepared = if (kind == PackageArchiveKind.APK) {
            prepareApk(sourceFile)
        } else {
            prepareContainer(sourceFile, kind, operationDirectory)
        }
        val base = prepared.selected.single(PackageApk::isBase).stagedFile
            ?: throw IOException("The base APK was not staged")
        val incoming = readPackageInfo(base)
        val label = incoming.applicationInfo?.let { applicationInfo ->
            applicationInfo.sourceDir = base.path
            applicationInfo.publicSourceDir = base.path
            runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }
                .getOrNull()
        }.orEmpty().ifBlank { prepared.packageName }
        val installed = installedPackageInfo(prepared.packageName)
        val requestedPermissions = incoming.requestedPermissions.orEmpty().distinct().sorted()
        val requestedFeatures = incoming.reqFeatures.orEmpty()
            .mapNotNull { it.name }
            .distinct()
            .sorted()
        val incomingComponents = components(incoming)
        val installedPermissions = installed?.requestedPermissions.orEmpty().toSet()
        val installedFeatures = installed?.reqFeatures.orEmpty().mapNotNull { it.name }.toSet()
        val installedComponents = installed?.let(::components).orEmpty().toSet()
        val incomingSignerDigests = normalizeCertificateDigests(
            prepared.signerCertificateSha256
        )
        val signerCompatible = installed == null || installedSignerDigests(installed).any {
            it in incomingSignerDigests
        }
        val incomingVersionCode = incoming.longVersionCodeCompat()
        val installedVersionCode = installed?.longVersionCodeCompat()
        val action = when {
            installedVersionCode == null -> PackageInstallAction.INSTALL
            incomingVersionCode > installedVersionCode -> PackageInstallAction.UPDATE
            incomingVersionCode < installedVersionCode -> PackageInstallAction.DOWNGRADE
            else -> PackageInstallAction.REINSTALL
        }
        val warnings = buildList {
            if (!signerCompatible) add("The installed app and incoming package have different signing certificates")
            if (action == PackageInstallAction.DOWNGRADE) add("Downgrading may be blocked and can make app data incompatible")
            if (prepared.excluded.isNotEmpty()) add(
                "${prepared.excluded.size} incompatible configuration APKs will not be installed"
            )
            if (prepared.expansions.isNotEmpty()) add(
                "Expansion files are installed after the APK session and cannot be part of the same atomic transaction"
            )
        }
        return PackageInstallPlan(
            operationId = operationId,
            archiveKind = kind,
            sourceFile = sourceFile,
            displayName = displayName,
            packageName = prepared.packageName,
            label = label,
            versionName = incoming.versionName,
            versionCode = incomingVersionCode,
            minimumSdk = incoming.applicationInfo?.minSdkVersion ?: 1,
            targetSdk = incoming.applicationInfo?.targetSdkVersion ?: 1,
            signerCertificateSha256 = incomingSignerDigests,
            apks = prepared.selected,
            excludedApks = prepared.excluded,
            expansions = prepared.expansions,
            requestedPermissions = requestedPermissions,
            requestedFeatures = requestedFeatures,
            components = incomingComponents,
            comparison = PackageInstallComparison(
                action = action,
                installedVersionName = installed?.versionName,
                installedVersionCode = installedVersionCode,
                signerCompatible = signerCompatible,
                addedPermissions = (requestedPermissions.toSet() - installedPermissions).sorted(),
                removedPermissions = (installedPermissions - requestedPermissions.toSet()).sorted(),
                addedFeatures = (requestedFeatures.toSet() - installedFeatures).sorted(),
                removedFeatures = (installedFeatures - requestedFeatures.toSet()).sorted(),
                addedComponents = (incomingComponents.toSet() - installedComponents).sorted(),
                removedComponents = (installedComponents - incomingComponents.toSet()).sorted(),
                warnings = warnings
            )
        )
    }

    private fun prepareApk(sourceFile: File): PreparedPackage {
        val verification = signatureBackend.verify(ApkVerificationRequest(sourceFile))
        if (!verification.verified) {
            throw IOException(
                verification.errors.joinToString("; ") { it.message }
                    .ifBlank { "APK signature is not verified" }
            )
        }
        val identity = identityReader.read(sourceFile, sourceFile.name)
        require(identity.splitName == null) { "A standalone APK must be a base APK" }
        val apk = PackageApk(
            entryName = "base.apk",
            splitName = null,
            sizeBytes = sourceFile.length(),
            sha256 = sourceFile.sha256(),
            stagedFile = sourceFile
        )
        return PreparedPackage(
            packageName = identity.packageName,
            signerCertificateSha256 = verification.signerCertificateSha256,
            selected = listOf(apk),
            excluded = emptyList(),
            expansions = emptyList()
        )
    }

    private fun prepareContainer(
        sourceFile: File,
        kind: PackageArchiveKind,
        operationDirectory: File
    ): PreparedPackage {
        val inventory = inspector.inspect(sourceFile)
        if (inventory.format == AndroidPackageContainerFormat.AAB) {
            throw IOException("AAB files must be converted with bundletool before installation")
        }
        val verificationDirectory = File(operationDirectory, "verification")
        val report = AndroidSplitSetVerifier(
            inspector = inspector,
            identityReader = identityReader
        ).verify(sourceFile, verificationDirectory)
        if (!report.verified) {
            throw IOException(summarizeSplitVerificationErrors(
                report.errors,
                passedApks = report.apks.size,
                totalApks = inventory.apkEntries.size
            ))
        }
        val packageName = report.packageName ?: throw IOException("Package name is missing")
        val versionCode = report.versionCode ?: throw IOException("Package version is missing")
        val inventoryByName = inventory.apkEntries.associateBy { it.name }
        val candidates = report.apks.map { verified ->
            val item = inventoryByName[verified.entryName]
                ?: throw IOException("Verified APK is absent from the archive inventory")
            PackageApk(
                entryName = item.name,
                splitName = verified.identity.splitName,
                sizeBytes = item.sizeBytes,
                sha256 = item.sha256
            )
        }
        val configuration = DeviceApkConfiguration(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            densityDpi = context.resources.displayMetrics.densityDpi,
            locales = context.resources.configuration.locales.let { locales ->
                (0 until locales.size()).map { locales[it].toLanguageTag() }
            }
        )
        val selection = PackageSplitSelector().select(candidates, configuration)
        val apksDirectory = File(operationDirectory, "apks").also {
            if (!it.exists() && !it.mkdirs()) throw IOException("Unable to create APK staging")
        }
        val selected = extractApks(sourceFile, selection.selected, apksDirectory)
        val expansions = if (kind == PackageArchiveKind.XAPK) {
            val obbEntries = inventory.entries.filter { it.name.endsWith(".obb", true) }
            val referencedNames = xapkExpansionNames(sourceFile)
            val unreferenced = obbEntries.filter { entry ->
                referencedNames.none { it.equals(entry.name, ignoreCase = true) }
            }
            require(unreferenced.isEmpty()) {
                "XAPK contains expansion files that are not declared by its manifest"
            }
            val entries = referencedNames.map { referenced ->
                obbEntries.singleOrNull { it.name.equals(referenced, ignoreCase = true) }
                    ?: throw IOException("XAPK manifest references a missing expansion file")
            }
            extractExpansions(
                sourceFile,
                entries.map {
                    PackageExpansion(it.name, it.name.substringAfterLast('/'), it.sizeBytes, it.sha256)
                },
                File(operationDirectory, "obb"),
                packageName,
                versionCode
            )
        } else {
            emptyList()
        }
        return PreparedPackage(
            packageName = packageName,
            signerCertificateSha256 = report.signerCertificateSha256,
            selected = selected,
            excluded = selection.excluded,
            expansions = expansions
        )
    }

    private fun xapkExpansionNames(sourceFile: File): List<String> = ZipFile(sourceFile).use { zip ->
        val entry = zip.entries().asSequence().firstOrNull {
            it.name.equals("manifest.json", ignoreCase = true)
        } ?: throw IOException("XAPK manifest is missing")
        require(entry.size in 1..2L * 1024L * 1024L) { "XAPK manifest is too large" }
        val json = zip.getInputStream(entry).use { input ->
            JSONObject(String(input.readBytes(), Charsets.UTF_8))
        }
        val expansions = json.optJSONArray("expansions") ?: return@use emptyList()
        buildList {
            for (index in 0 until expansions.length()) {
                val name = expansions.optJSONObject(index)?.optString("file").orEmpty()
                if (name.isNotBlank() && name.endsWith(".obb", ignoreCase = true)) add(name)
            }
        }.also { names ->
            require(names.distinctBy { it.lowercase(Locale.ROOT) }.size == names.size) {
                "XAPK manifest contains duplicate expansion references"
            }
        }
    }

    private fun extractApks(
        sourceFile: File,
        apks: List<PackageApk>,
        targetDirectory: File
    ): List<PackageApk> = ZipFile(sourceFile).use { zip ->
        apks.mapIndexed { index, apk ->
            val entry = zip.getEntry(apk.entryName)
                ?: throw IOException("APK entry disappeared during preparation")
            val target = File(targetDirectory, "%04d-%s.apk".format(
                Locale.ROOT,
                index,
                if (apk.isBase) "base" else "split"
            ))
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() != apk.sizeBytes || !target.sha256().equals(apk.sha256, true)) {
                target.delete()
                throw IOException("APK entry changed while it was being staged")
            }
            apk.copy(stagedFile = target)
        }
    }

    private fun extractExpansions(
        sourceFile: File,
        expansions: List<PackageExpansion>,
        targetDirectory: File,
        packageName: String,
        versionCode: Long
    ): List<PackageExpansion> {
        if (expansions.isEmpty()) return emptyList()
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw IOException("Unable to create expansion staging")
        }
        val expected = Regex("^(main|patch)\\.$versionCode\\.${Regex.escape(packageName)}\\.obb$")
        require(expansions.map(PackageExpansion::fileName).distinct().size == expansions.size) {
            "XAPK contains duplicate expansion file names"
        }
        return ZipFile(sourceFile).use { zip ->
            expansions.map { expansion ->
                require(expected.matches(expansion.fileName)) {
                    "Expansion file does not match the package name and version"
                }
                val entry = zip.getEntry(expansion.entryName)
                    ?: throw IOException("Expansion entry disappeared during preparation")
                val target = File(targetDirectory, expansion.fileName)
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.length() != expansion.sizeBytes ||
                    !target.sha256().equals(expansion.sha256, true)) {
                    target.delete()
                    throw IOException("Expansion entry changed while it was being staged")
                }
                expansion.copy(stagedFile = target)
            }
        }
    }

    private fun readPackageInfo(apk: File): PackageInfo {
        val flags = PackageManager.GET_PERMISSIONS.toLong() or
            PackageManager.GET_CONFIGURATIONS.toLong() or
            PackageManager.GET_ACTIVITIES.toLong() or PackageManager.GET_SERVICES.toLong() or
            PackageManager.GET_RECEIVERS.toLong() or PackageManager.GET_PROVIDERS.toLong()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION") packageManager.getPackageArchiveInfo(apk.path, flags.toInt())
        } ?: throw IOException("Unable to read base APK metadata")
    }

    private fun installedPackageInfo(packageName: String): PackageInfo? {
        val flags = PackageManager.GET_PERMISSIONS.toLong() or
            PackageManager.GET_CONFIGURATIONS.toLong() or
            PackageManager.GET_ACTIVITIES.toLong() or PackageManager.GET_SERVICES.toLong() or
            PackageManager.GET_RECEIVERS.toLong() or PackageManager.GET_PROVIDERS.toLong() or
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, flags.toInt())
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun components(info: PackageInfo): List<String> = buildList {
        info.activities.orEmpty().mapTo(this) { it.name }
        info.services.orEmpty().mapTo(this) { it.name }
        info.receivers.orEmpty().mapTo(this) { it.name }
        info.providers.orEmpty().mapTo(this) { it.name }
    }.distinct().sorted()

    private fun installedSignerDigests(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        val signers = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        return signers.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHexString()
        }
    }

    private fun PackageInfo.longVersionCodeCompat(): Long = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    ) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

    private fun File.sha256(): String = inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0').uppercase(Locale.ROOT)
    }

    private data class PreparedPackage(
        val packageName: String,
        val signerCertificateSha256: List<String>,
        val selected: List<PackageApk>,
        val excluded: List<PackageApk>,
        val expansions: List<PackageExpansion>
    )
}

internal fun summarizeSplitVerificationErrors(
    errors: List<String>,
    passedApks: Int,
    totalApks: Int
): String {
    if (errors.isEmpty()) return "Split package verification failed"
    val grouped = errors.groupBy { error ->
        error.substringAfter(": ", missingDelimiterValue = error)
    }
    val details = grouped.entries.take(MAXIMUM_VERIFICATION_ERROR_GROUPS).map { (reason, entries) ->
        if (entries.size == 1) {
            entries.single()
        } else {
            val examples = entries.take(MAXIMUM_VERIFICATION_ERROR_EXAMPLES).joinToString(", ") {
                it.substringBefore(": ")
            }
            val omitted = entries.size - MAXIMUM_VERIFICATION_ERROR_EXAMPLES
            buildString {
                append(entries.size).append(" APKs: ").append(reason).append(" (").append(examples)
                if (omitted > 0) append(", +").append(omitted).append(" more")
                append(')')
            }
        }
    }
    val omittedGroups = grouped.size - details.size
    return buildString {
        append(passedApks).append(" of ").append(totalApks)
            .append(" APKs passed verification.")
        details.forEach { append('\n').append(it) }
        if (omittedGroups > 0) {
            append('\n').append(omittedGroups).append(" additional error groups omitted.")
        }
    }
}

private const val MAXIMUM_VERIFICATION_ERROR_GROUPS = 4
private const val MAXIMUM_VERIFICATION_ERROR_EXAMPLES = 3
