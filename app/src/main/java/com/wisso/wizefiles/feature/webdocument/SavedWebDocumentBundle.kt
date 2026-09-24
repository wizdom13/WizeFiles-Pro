package com.wisso.wizefiles.feature.webdocument

import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class SavedWebDocumentBundle(
    val startUrl: String,
    private val resources: Map<String, Resource>
) {
    data class Resource(val file: File, val mimeType: String, val charset: String? = null)

    fun resourceFor(url: String): Resource? = normalizeUrl(url)?.let(resources::get)

    fun contains(url: String): Boolean = resourceFor(url) != null

    companion object {
        const val VIRTUAL_ROOT = "https://saved.invalid/document/"
        const val VIRTUAL_INDEX = "${VIRTUAL_ROOT}index.html"

        fun singleHtml(file: File): SavedWebDocumentBundle = SavedWebDocumentBundle(
            VIRTUAL_INDEX,
            mapOf(normalizeUrl(VIRTUAL_INDEX)!! to Resource(file, "text/html"))
        )

        fun extractedDirectory(root: File, files: List<File>): SavedWebDocumentBundle {
            val html = files
                .filter { extensionOf(it.name) in setOf("html", "htm", "xhtml", "hhc") }
                .minWithOrNull(compareBy<File>({ startPageScore(it.name) }, { it.relativeTo(root).invariantSeparatorsPath.count { c -> c == '/' } }, { it.name.lowercase(Locale.ROOT) }))
                ?: throw UnsafeSavedWebDocumentException("Saved document has no HTML entry point")
            val resources = linkedMapOf<String, Resource>()
            files.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                val url = VIRTUAL_ROOT + encodeRelativePath(relative)
                resources[normalizeUrl(url)!!] = Resource(file, mimeTypeFor(file.name))
            }
            val start = VIRTUAL_ROOT + encodeRelativePath(html.relativeTo(root).invariantSeparatorsPath)
            return SavedWebDocumentBundle(start, resources)
        }

        internal fun fromMhtmlParts(parts: List<MhtmlPart>, rootIndex: Int): SavedWebDocumentBundle {
            val rootPart = parts[rootIndex]
            val resources = linkedMapOf<String, Resource>()
            val rootOriginal = rootPart.location?.let(::parseUri)
            val virtualBase = URI(VIRTUAL_ROOT)
            parts.forEachIndexed { index, part ->
                val resource = Resource(part.file, part.mimeType, part.charset)
                if (index == rootIndex) {
                    resources[normalizeUrl(VIRTUAL_INDEX)!!] = resource
                }
                part.contentId?.trim('<', '>')?.takeIf(String::isNotBlank)?.let {
                    normalizeUrl("cid:$it")?.let { key -> resources[key] = resource }
                }
                val location = part.location?.let(::parseUri)
                if (location != null) {
                    val absolute = when {
                        location.isAbsolute -> location
                        rootOriginal != null -> rootOriginal.resolve(location)
                        else -> null
                    }
                    absolute?.toASCIIString()?.let(::normalizeUrl)?.let { resources[it] = resource }
                    val relative = when {
                        !location.isAbsolute -> location
                        rootOriginal != null && absolute != null -> rootOriginal.resolve(".").relativize(absolute)
                        else -> null
                    }
                    if (relative != null && !relative.isAbsolute && relative.toString().isNotBlank()) {
                        normalizeUrl(virtualBase.resolve(relative).toASCIIString())?.let { resources[it] = resource }
                    }
                }
            }
            return SavedWebDocumentBundle(VIRTUAL_INDEX, resources)
        }

        internal fun normalizeUrl(raw: String): String? {
            return try {
                val uri = parseUri(raw.substringBefore('#')) ?: return null
                uri.normalize().toASCIIString()
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun parseUri(raw: String): URI? = try {
            URI(raw.replace(" ", "%20"))
        } catch (_: Exception) {
            null
        }

        private fun encodeRelativePath(path: String): String = path.split('/').joinToString("/") {
            URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }

        private fun startPageScore(name: String): Int = when (name.lowercase(Locale.ROOT)) {
            "index.html" -> 0
            "index.htm" -> 1
            "default.html" -> 2
            "default.htm" -> 3
            else -> 10
        }

        internal fun mimeTypeFor(name: String): String = when (extensionOf(name)) {
            "html", "htm", "xhtml", "hhc" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }

        private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    }
}

internal data class MhtmlPart(
    val file: File,
    val mimeType: String,
    val charset: String?,
    val location: String?,
    val contentId: String?
)

class UnsafeSavedWebDocumentException(message: String, cause: Throwable? = null) : Exception(message, cause)
