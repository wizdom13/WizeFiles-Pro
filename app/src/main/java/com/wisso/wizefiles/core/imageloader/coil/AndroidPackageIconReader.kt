// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.imageloader.coil

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal enum class AndroidPackageArchiveKind {
    AAB,
    APKS,
    XAPK,
    APKM;

    companion object {
        fun fromPath(path: String): AndroidPackageArchiveKind? = when (
            path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        ) {
            "aab" -> AAB
            "apks" -> APKS
            "xapk" -> XAPK
            "apkm" -> APKM
            else -> null
        }
    }
}

internal sealed interface AndroidPackageIconPayload {
    val entryName: String

    data class NestedApk(override val entryName: String) : AndroidPackageIconPayload
    data class Raster(override val entryName: String) : AndroidPackageIconPayload
}

/**
 * Performs the cheap, bounded part of package-container icon discovery.
 *
 * This deliberately does not run the full package-container signing inspector: that inspector
 * hashes every entry, which is correct for signing but too expensive while scrolling a file list.
 */
internal class AndroidPackageIconReader(
    private val maximumEntries: Int = 4096,
    private val maximumPathLength: Int = 512,
    private val maximumMetadataBytes: Long = 8L * 1024 * 1024,
    private val maximumSelectedEntryBytes: Long = 512L * 1024 * 1024,
    private val maximumCompressionRatio: Long = 250
) {
    fun find(file: File, kind: AndroidPackageArchiveKind): AndroidPackageIconPayload? =
        ZipFile(file).use { archive ->
            val entries = validatedEntries(archive)
            when (kind) {
                AndroidPackageArchiveKind.AAB -> findAabRaster(archive, entries)
                AndroidPackageArchiveKind.APKS,
                AndroidPackageArchiveKind.XAPK,
                AndroidPackageArchiveKind.APKM -> findNestedApk(archive, entries, kind)
            }
        }

    private fun validatedEntries(archive: ZipFile): List<ZipEntry> {
        val result = ArrayList<ZipEntry>()
        val names = HashSet<String>()
        val iterator = archive.entries()
        while (iterator.hasMoreElements()) {
            require(result.size < maximumEntries) { "Package container has too many entries" }
            val entry = iterator.nextElement()
            validateName(entry.name)
            val folded = entry.name.lowercase(Locale.ROOT)
            require(names.add(folded)) { "Package container has duplicate entry names" }
            validateDeclaredSize(entry)
            result += entry
        }
        return result
    }

    private fun findNestedApk(
        archive: ZipFile,
        entries: List<ZipEntry>,
        kind: AndroidPackageArchiveKind
    ): AndroidPackageIconPayload? {
        val apkEntries = entries.filter { !it.isDirectory && it.name.endsWith(".apk", true) }
        if (apkEntries.isEmpty()) return null

        val metadataHint = when (kind) {
            AndroidPackageArchiveKind.XAPK -> readMetadataHint(archive, entries, "manifest.json")
            AndroidPackageArchiveKind.APKM -> readMetadataHint(archive, entries, "info.json")
            AndroidPackageArchiveKind.APKS -> readMetadataHint(archive, entries, "metadata.json")
            AndroidPackageArchiveKind.AAB -> null
        }
        val selected = apkEntries.minWithOrNull(
            compareBy<ZipEntry> { apkRank(it.name, metadataHint) }
                .thenBy { it.name.count { character -> character == '/' } }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        ) ?: return null
        validateSelectedEntry(selected)
        return AndroidPackageIconPayload.NestedApk(selected.name)
    }

    private fun apkRank(name: String, metadataHint: String?): Int {
        val normalized = name.lowercase(Locale.ROOT)
        val leaf = normalized.substringAfterLast('/')
        return when {
            metadataHint != null && name == metadataHint -> 0
            leaf == "universal.apk" -> 1
            normalized.startsWith("standalones/") -> 2
            leaf == "base.apk" -> 3
            leaf == "base-master.apk" -> 4
            leaf == "master.apk" -> 5
            "base-master" in leaf -> 6
            "config." in leaf || "split_config" in leaf -> 20
            else -> 10
        }
    }

    private fun readMetadataHint(
        archive: ZipFile,
        entries: List<ZipEntry>,
        metadataName: String
    ): String? {
        val entry = entries.firstOrNull { it.name.equals(metadataName, true) } ?: return null
        if (entry.size !in 1..maximumMetadataBytes) return null
        val text = archive.getInputStream(entry).use { input ->
            input.readBoundedBytes(maximumMetadataBytes).toString(Charsets.UTF_8)
        }
        val apkNames = Regex("[A-Za-z0-9_./ -]+\\.apk", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.value.trim() }
            .filter { candidate -> entries.any { it.name == candidate } }
            .toList()
        return apkNames.minByOrNull { apkRank(it, null) }
    }

    private fun findAabRaster(
        archive: ZipFile,
        entries: List<ZipEntry>
    ): AndroidPackageIconPayload? {
        val manifestTokens = readProtoStrings(archive, entries, "base/manifest/AndroidManifest.xml")
        val candidates = entries.filter { entry ->
            val name = entry.name.lowercase(Locale.ROOT)
            !entry.isDirectory && name.startsWith("base/res/") &&
                ("/mipmap" in name || "/drawable" in name) &&
                RASTER_EXTENSIONS.any(name::endsWith) && !name.endsWith(".9.png")
        }
        val selected = candidates.minWithOrNull(
            compareBy<ZipEntry> { aabRasterRank(it.name, manifestTokens) }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        ) ?: return null
        validateSelectedEntry(selected)
        return AndroidPackageIconPayload.Raster(selected.name)
    }

    private fun readProtoStrings(
        archive: ZipFile,
        entries: List<ZipEntry>,
        name: String
    ): Set<String> {
        val entry = entries.firstOrNull { it.name == name } ?: return emptySet()
        if (entry.size !in 1..maximumMetadataBytes) return emptySet()
        val bytes = archive.getInputStream(entry).use { it.readBoundedBytes(maximumMetadataBytes) }
        val text = bytes.toString(Charsets.UTF_8).lowercase(Locale.ROOT)
        return ICON_WORDS.filterTo(linkedSetOf()) { it in text }
    }

    private fun aabRasterRank(name: String, manifestTokens: Set<String>): Int {
        val normalized = name.lowercase(Locale.ROOT)
        val leaf = normalized.substringAfterLast('/').substringBeforeLast('.')
        val semantic = when {
            manifestTokens.any { it in leaf } -> 0
            leaf == "ic_launcher" || leaf == "launcher_icon" -> 2
            "launcher" in leaf -> 4
            leaf == "app_icon" || leaf == "icon" -> 6
            "icon" in leaf -> 8
            else -> 30
        }
        val density = when {
            "xxxhdpi" in normalized -> 0
            "xxhdpi" in normalized -> 1
            "xhdpi" in normalized -> 2
            "hdpi" in normalized -> 3
            "mdpi" in normalized -> 4
            "nodpi" in normalized -> 5
            else -> 6
        }
        return semantic * 10 + density
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && name.length <= maximumPathLength) {
            "Package container has an invalid entry name"
        }
        require('\u0000' !in name && '\r' !in name && '\n' !in name && '\\' !in name &&
            !name.startsWith('/') && !DRIVE_PATH.matches(name)) {
            "Package container has an unsafe entry name"
        }
        require(Normalizer.normalize(name, Normalizer.Form.NFC) == name) {
            "Package container entry name is not normalized"
        }
        require(name.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "Package container entry uses traversal"
        }
    }

    private fun validateDeclaredSize(entry: ZipEntry) {
        require(entry.size >= -1L && entry.compressedSize >= -1L) {
            "Package container has invalid entry sizes"
        }
        if (entry.size > 0 && entry.compressedSize == 0L) {
            throw IOException("Package container entry has an unsafe compression ratio")
        }
        if (entry.size > 0 && entry.compressedSize > 0 &&
            entry.size / entry.compressedSize > maximumCompressionRatio) {
            throw IOException("Package container entry has an unsafe compression ratio")
        }
    }

    private fun validateSelectedEntry(entry: ZipEntry) {
        require(entry.size in 0..maximumSelectedEntryBytes) {
            "Package icon entry is too large or has an unknown size"
        }
    }

    private companion object {
        val DRIVE_PATH = Regex("^[A-Za-z]:.*")
        val RASTER_EXTENSIONS = listOf(".png", ".webp", ".jpg", ".jpeg")
        val ICON_WORDS = setOf("ic_launcher", "launcher_icon", "app_icon", "icon")
    }
}

private fun InputStream.readBoundedBytes(maximumBytes: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        total += count
        if (total > maximumBytes) throw IOException("Package metadata exceeds its limit")
        output.write(buffer, 0, count)
    }
}
