// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import org.json.JSONObject

class AndroidPackageContainerInspector(
    private val limits: AndroidPackageContainerLimits = AndroidPackageContainerLimits()
) {
    fun inspect(
        file: File,
        hint: AndroidPackageContainerHint? = hintFromName(file.name)
    ): AndroidPackageContainerInventory {
        require(file.isFile && file.canRead()) { "Container is not a readable regular file" }
        require(file.length() in 1..limits.maximumArchiveBytes) {
            "Container exceeds the allowed archive size"
        }

        try {
            ZipFile(file).use { zip ->
                val zipEntries = zip.entries().asSequence().toList()
                if (zipEntries.isEmpty()) throw AndroidPackageContainerException(
                    "Package container is empty"
                )
                if (zipEntries.size > limits.maximumEntries) throw AndroidPackageContainerException(
                    "Package container has too many entries"
                )

                val seenNames = linkedSetOf<String>()
                val entries = ArrayList<AndroidPackageContainerEntry>(zipEntries.size)
                val metadata = linkedMapOf<String, ByteArray>()
                var expandedBytes = 0L
                var apkCount = 0

                zipEntries.forEach { entry ->
                    val name = validateName(entry.name, seenNames)
                    if (entry.isDirectory) return@forEach
                    val isApk = name.endsWith(".apk", ignoreCase = true)
                    if (isApk && ++apkCount > limits.maximumApkEntries) {
                        throw AndroidPackageContainerException(
                            "Package container has too many APK entries"
                        )
                    }
                    validateDeclaredSize(entry)
                    expandedBytes = addExact(expandedBytes, entry.size.coerceAtLeast(0))
                    if (expandedBytes > limits.maximumExpandedBytes) {
                        throw AndroidPackageContainerException(
                            "Package container expands beyond the allowed size"
                        )
                    }

                    val captureMetadata = isMetadata(name)
                    val result = zip.getInputStream(entry).use { input ->
                        readEntry(input, entry, captureMetadata)
                    }
                    expandedBytes = addExact(
                        expandedBytes - entry.size.coerceAtLeast(0),
                        result.sizeBytes
                    )
                    if (expandedBytes > limits.maximumExpandedBytes) {
                        throw AndroidPackageContainerException(
                            "Package container expands beyond the allowed size"
                        )
                    }
                    result.metadata?.let { metadata[name.lowercase(Locale.ROOT)] = it }
                    entries += AndroidPackageContainerEntry(
                        name = name,
                        sizeBytes = result.sizeBytes,
                        compressedBytes = entry.compressedSize.coerceAtLeast(0),
                        sha256 = result.sha256,
                        isApk = isApk
                    )
                }

                val format = detectFormat(entries, metadata, hint)
                val packageHint = packageNameHint(format, metadata)
                val versionHint = versionCodeHint(format, metadata)
                validateMetadata(format, entries, metadata)
                return AndroidPackageContainerInventory(
                    format = format,
                    entries = entries,
                    apkEntries = if (format == AndroidPackageContainerFormat.AAB) {
                        emptyList()
                    } else {
                        entries.filter(AndroidPackageContainerEntry::isApk)
                    },
                    packageNameHint = packageHint,
                    versionCodeHint = versionHint
                )
            }
        } catch (exception: Exception) {
            if (exception is AndroidPackageContainerException) throw exception
            if (exception is IllegalArgumentException) throw exception
            val reason = if (exception is ZipException) {
                "Container is not a supported ZIP archive"
            } else {
                "Unable to inspect package container"
            }
            throw AndroidPackageContainerException(reason, exception)
        }
    }

    private fun validateName(rawName: String, seenNames: MutableSet<String>): String {
        if (rawName.isBlank() || rawName.length > limits.maximumPathLength) {
            throw AndroidPackageContainerException("Container entry has an invalid path")
        }
        if ('\u0000' in rawName || '\\' in rawName || rawName.startsWith('/') ||
            DRIVE_PATH.matches(rawName)) {
            throw AndroidPackageContainerException("Container entry uses an unsafe path")
        }
        val name = Normalizer.normalize(rawName, Normalizer.Form.NFC)
        if (name != rawName) {
            throw AndroidPackageContainerException("Container entry path is not normalized")
        }
        val segments = name.trimEnd('/').split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw AndroidPackageContainerException("Container entry uses path traversal")
        }
        val collisionKey = name.trimEnd('/').lowercase(Locale.ROOT)
        if (!seenNames.add(collisionKey)) {
            throw AndroidPackageContainerException("Container has duplicate entry paths")
        }
        return name
    }

    private fun validateDeclaredSize(entry: ZipEntry) {
        if (entry.size < 0 || entry.compressedSize < 0) {
            throw AndroidPackageContainerException("Container entry has an unknown size")
        }
        if (entry.size > limits.maximumEntryBytes) {
            throw AndroidPackageContainerException("Container entry exceeds the allowed size")
        }
        val compressed = entry.compressedSize.coerceAtLeast(1)
        if (entry.size > compressed * limits.maximumCompressionRatio) {
            throw AndroidPackageContainerException("Container entry has an unsafe compression ratio")
        }
    }

    private fun readEntry(
        input: InputStream,
        entry: ZipEntry,
        captureMetadata: Boolean
    ): ReadResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val metadata = if (captureMetadata) ByteArrayOutputStream() else null
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var countBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            countBytes = addExact(countBytes, count.toLong())
            if (countBytes > limits.maximumEntryBytes) {
                throw AndroidPackageContainerException("Container entry exceeds the allowed size")
            }
            digest.update(buffer, 0, count)
            metadata?.let {
                if (countBytes > limits.maximumMetadataBytes) {
                    throw AndroidPackageContainerException("Container metadata exceeds the allowed size")
                }
                it.write(buffer, 0, count)
            }
        }
        if (countBytes != entry.size) {
            throw AndroidPackageContainerException("Container entry size does not match its directory")
        }
        return ReadResult(countBytes, digest.digest().toHexString(), metadata?.toByteArray())
    }

    private fun detectFormat(
        entries: List<AndroidPackageContainerEntry>,
        metadata: Map<String, ByteArray>,
        hint: AndroidPackageContainerHint?
    ): AndroidPackageContainerFormat {
        val names = entries.mapTo(linkedSetOf()) { it.name.lowercase(Locale.ROOT) }
        val detected = when {
            "bundleconfig.pb" in names && "base/manifest/androidmanifest.xml" in names ->
                AndroidPackageContainerFormat.AAB
            "toc.pb" in names && entries.any(AndroidPackageContainerEntry::isApk) ->
                AndroidPackageContainerFormat.BUNDLETOOL_APKS
            "metadata.json" in names && "base.apk" in names ->
                AndroidPackageContainerFormat.WIZEFILES_APKS
            "manifest.json" in names && entries.any(AndroidPackageContainerEntry::isApk) ->
                AndroidPackageContainerFormat.XAPK
            entries.any { it.name.equals("base.apk", ignoreCase = true) } &&
                entries.any(AndroidPackageContainerEntry::isApk) ->
                AndroidPackageContainerFormat.APKM
            else -> throw AndroidPackageContainerException(
                "Archive is not a recognized Android package container"
            )
        }
        val compatible = when (hint) {
            null -> true
            AndroidPackageContainerHint.AAB -> detected == AndroidPackageContainerFormat.AAB
            AndroidPackageContainerHint.APKS -> detected == AndroidPackageContainerFormat.BUNDLETOOL_APKS ||
                detected == AndroidPackageContainerFormat.WIZEFILES_APKS
            AndroidPackageContainerHint.XAPK -> detected == AndroidPackageContainerFormat.XAPK
            AndroidPackageContainerHint.APKM -> detected == AndroidPackageContainerFormat.APKM
        }
        if (!compatible) throw AndroidPackageContainerException(
            "Container contents do not match the selected file format"
        )
        return detected
    }

    private fun validateMetadata(
        format: AndroidPackageContainerFormat,
        entries: List<AndroidPackageContainerEntry>,
        metadata: Map<String, ByteArray>
    ) {
        val byName = entries.associateBy { it.name.lowercase(Locale.ROOT) }
        when (format) {
            AndroidPackageContainerFormat.WIZEFILES_APKS -> {
                val json = metadata.json("metadata.json")
                val apks = json.optJSONArray("apks") ?: throw AndroidPackageContainerException(
                    "WizeFiles APKS metadata has no APK inventory"
                )
                for (index in 0 until apks.length()) {
                    val item = apks.optJSONObject(index) ?: throw AndroidPackageContainerException(
                        "WizeFiles APKS metadata has an invalid APK record"
                    )
                    val file = item.optString("file").lowercase(Locale.ROOT)
                    val expected = item.optString("sha256")
                    val actual = byName[file] ?: throw AndroidPackageContainerException(
                        "WizeFiles APKS metadata references a missing APK"
                    )
                    if (!expected.equals(actual.sha256, ignoreCase = true)) {
                        throw AndroidPackageContainerException(
                            "WizeFiles APKS checksum metadata does not match"
                        )
                    }
                }
            }
            AndroidPackageContainerFormat.XAPK -> {
                val json = metadata.json("manifest.json")
                if (json.string("package_name", "packageName").isNullOrBlank()) {
                    throw AndroidPackageContainerException("XAPK manifest has no package name")
                }
                json.optJSONArray("split_apks")?.let { splits ->
                    for (index in 0 until splits.length()) {
                        val item = splits.optJSONObject(index) ?: continue
                        val file = item.optString("file").lowercase(Locale.ROOT)
                        if (file.isNotBlank() && byName[file]?.isApk != true) {
                            throw AndroidPackageContainerException(
                                "XAPK manifest references a missing split APK"
                            )
                        }
                    }
                }
                json.optJSONArray("expansions")?.let { expansions ->
                    for (index in 0 until expansions.length()) {
                        val item = expansions.optJSONObject(index) ?: continue
                        val file = item.optString("file").lowercase(Locale.ROOT)
                        if (file.isNotBlank() && file !in byName) {
                            throw AndroidPackageContainerException(
                                "XAPK manifest references a missing expansion file"
                            )
                        }
                    }
                }
            }
            AndroidPackageContainerFormat.AAB -> {
                if (byName["bundleconfig.pb"]?.sizeBytes == 0L) {
                    throw AndroidPackageContainerException("AAB BundleConfig.pb is empty")
                }
            }
            AndroidPackageContainerFormat.BUNDLETOOL_APKS -> {
                if (byName["toc.pb"]?.sizeBytes == 0L) {
                    throw AndroidPackageContainerException("APKS toc.pb is empty")
                }
            }
            AndroidPackageContainerFormat.APKM -> {
                metadata["info.json"]?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }
                    .getOrElse { cause -> throw AndroidPackageContainerException(
                        "APKM info.json is invalid", cause
                    ) } }
            }
        }
    }

    private fun packageNameHint(
        format: AndroidPackageContainerFormat,
        metadata: Map<String, ByteArray>
    ): String? = when (format) {
        AndroidPackageContainerFormat.WIZEFILES_APKS ->
            metadata.json("metadata.json").optString("packageName").takeIf(String::isNotBlank)
        AndroidPackageContainerFormat.XAPK ->
            metadata.json("manifest.json").string("package_name", "packageName")
        AndroidPackageContainerFormat.APKM -> metadata["info.json"]?.let {
            runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull()
                ?.string("package_name", "packageName", "pname")
        }
        else -> null
    }

    private fun versionCodeHint(
        format: AndroidPackageContainerFormat,
        metadata: Map<String, ByteArray>
    ): Long? = when (format) {
        AndroidPackageContainerFormat.WIZEFILES_APKS ->
            metadata.json("metadata.json").optLong("versionCode").takeIf { it > 0 }
        AndroidPackageContainerFormat.XAPK ->
            metadata.json("manifest.json").long("version_code", "versionCode")
        AndroidPackageContainerFormat.APKM -> metadata["info.json"]?.let {
            runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull()
                ?.long("version_code", "versionCode")
        }
        else -> null
    }

    private fun Map<String, ByteArray>.json(name: String): JSONObject {
        val bytes = this[name] ?: throw AndroidPackageContainerException(
            "Container metadata $name is missing"
        )
        return runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }
            .getOrElse { throw AndroidPackageContainerException(
                "Container metadata $name is invalid", it
            ) }
    }

    private fun JSONObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        optString(key).takeIf(String::isNotBlank)
    }

    private fun JSONObject.long(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        optLong(key).takeIf { it > 0 }
    }

    private fun isMetadata(name: String): Boolean = name.equals("manifest.json", true) ||
        name.equals("metadata.json", true) || name.equals("info.json", true)

    private fun addExact(left: Long, right: Long): Long = try {
        Math.addExact(left, right)
    } catch (exception: ArithmeticException) {
        throw AndroidPackageContainerException("Container size accounting overflowed", exception)
    }

    private fun ByteArray.toHexString(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private data class ReadResult(
        val sizeBytes: Long,
        val sha256: String,
        val metadata: ByteArray?
    )

    companion object {
        private val DRIVE_PATH = Regex("^[A-Za-z]:.*")

        fun hintFromName(name: String): AndroidPackageContainerHint? = when (
            name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        ) {
            "aab" -> AndroidPackageContainerHint.AAB
            "apks" -> AndroidPackageContainerHint.APKS
            "xapk" -> AndroidPackageContainerHint.XAPK
            "apkm" -> AndroidPackageContainerHint.APKM
            else -> null
        }
    }
}
