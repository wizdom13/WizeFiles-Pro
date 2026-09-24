package com.wisso.wizefiles.feature.webdocument

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

object MhtmlBundleParser {
    const val MAX_SOURCE_BYTES = 16L * 1024 * 1024
    const val MAX_PART_BYTES = 8L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
    const val MAX_PARTS = 2_000

    @Throws(IOException::class, UnsafeSavedWebDocumentException::class)
    fun parse(source: File, outputDirectory: File): SavedWebDocumentBundle {
        if (!source.isFile || source.length() !in 1..MAX_SOURCE_BYTES) {
            throw UnsafeSavedWebDocumentException("MHTML source exceeds the safety limit")
        }
        val raw = source.readBytes().toString(StandardCharsets.ISO_8859_1)
        val documentHeaderEnd = headerEnd(raw, 0)
        if (documentHeaderEnd < 0) throw UnsafeSavedWebDocumentException("MHTML headers are missing")
        val documentHeaders = parseHeaders(raw.substring(0, documentHeaderEnd))
        val contentType = documentHeaders["content-type"].orEmpty()
        val boundary = parameter(contentType, "boundary")
            ?.takeIf { it.length in 1..200 && it.none { c -> c == '\r' || c == '\n' } }
            ?: throw UnsafeSavedWebDocumentException("MHTML boundary is missing")
        val startId = parameter(contentType, "start")?.trim('<', '>')
        val chunks = raw.substring(documentHeaderEnd).split("--$boundary")
        if (chunks.size - 2 > MAX_PARTS) throw UnsafeSavedWebDocumentException("MHTML has too many parts")
        if (!outputDirectory.isDirectory && !outputDirectory.mkdirs()) throw IOException("Unable to create MHTML cache")
        var total = 0L
        val parts = mutableListOf<MhtmlPart>()
        for (chunkValue in chunks.drop(1)) {
            var chunk = chunkValue.trimStart('\r', '\n')
            if (chunk.startsWith("--")) break
            chunk = chunk.trimEnd('\r', '\n')
            if (chunk.isBlank()) continue
            val end = headerEnd(chunk, 0)
            if (end < 0) throw UnsafeSavedWebDocumentException("MHTML part headers are missing")
            val separatorLength = if (chunk.startsWith("\r\n\r\n", end)) 4 else 2
            val headers = parseHeaders(chunk.substring(0, end))
            val body = chunk.substring(end + separatorLength)
            val decoded = decode(body, headers["content-transfer-encoding"])
            if (decoded.size.toLong() > MAX_PART_BYTES || total + decoded.size > MAX_TOTAL_BYTES) {
                throw UnsafeSavedWebDocumentException("MHTML resources exceed the safety limit")
            }
            total += decoded.size
            val typeHeader = headers["content-type"].orEmpty()
            val mime = typeHeader.substringBefore(';').trim().lowercase(Locale.ROOT)
                .ifBlank { SavedWebDocumentBundle.mimeTypeFor(headers["content-location"].orEmpty()) }
            val charset = parameter(typeHeader, "charset")
            val partFile = File(outputDirectory, "part-${parts.size.toString().padStart(4, '0')}.bin")
            partFile.outputStream().use { it.write(decoded) }
            parts += MhtmlPart(partFile, mime, charset, headers["content-location"]?.trim(), headers["content-id"]?.trim())
            if (parts.size > MAX_PARTS) throw UnsafeSavedWebDocumentException("MHTML has too many parts")
        }
        val rootIndex = when {
            startId != null -> parts.indexOfFirst { it.contentId?.trim('<', '>') == startId }
            else -> -1
        }.takeIf { it >= 0 } ?: parts.indexOfFirst { it.mimeType == "text/html" || it.mimeType == "application/xhtml+xml" }
        if (rootIndex < 0) throw UnsafeSavedWebDocumentException("MHTML has no HTML root")
        return SavedWebDocumentBundle.fromMhtmlParts(parts, rootIndex)
    }

    private fun decode(body: String, transferEncoding: String?): ByteArray = try {
        when (transferEncoding?.trim()?.lowercase(Locale.ROOT)) {
            "base64" -> Base64.getMimeDecoder().decode(body)
            "quoted-printable" -> decodeQuotedPrintable(body.toByteArray(StandardCharsets.ISO_8859_1))
            else -> body.toByteArray(StandardCharsets.ISO_8859_1)
        }
    } catch (exception: IllegalArgumentException) {
        throw UnsafeSavedWebDocumentException("MHTML transfer encoding is invalid", exception)
    }

    internal fun decodeQuotedPrintable(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(input.size)
        var index = 0
        while (index < input.size) {
            if (input[index] == '='.code.toByte()) {
                if (index + 2 < input.size && input[index + 1] == '\r'.code.toByte() && input[index + 2] == '\n'.code.toByte()) {
                    index += 3
                    continue
                }
                if (index + 1 < input.size && input[index + 1] == '\n'.code.toByte()) {
                    index += 2
                    continue
                }
                if (index + 2 < input.size) {
                    val high = hex(input[index + 1])
                    val low = hex(input[index + 2])
                    if (high >= 0 && low >= 0) {
                        output.write((high shl 4) or low)
                        index += 3
                        continue
                    }
                }
            }
            output.write(input[index].toInt())
            index++
        }
        return output.toByteArray()
    }

    private fun hex(value: Byte): Int = Character.digit(value.toInt().toChar(), 16)

    private fun headerEnd(value: String, start: Int): Int {
        val crlf = value.indexOf("\r\n\r\n", start)
        val lf = value.indexOf("\n\n", start)
        return when {
            crlf < 0 -> lf
            lf < 0 -> crlf
            else -> minOf(crlf, lf)
        }
    }

    internal fun parseHeaders(block: String): Map<String, String> {
        if (block.length > 64 * 1024) throw UnsafeSavedWebDocumentException("MHTML headers are too large")
        val unfolded = block.replace(Regex("\\r?\\n[ \\t]+"), " ")
        return unfolded.lineSequence().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).trim().lowercase(Locale.ROOT) to line.substring(separator + 1).trim()
        }.toMap()
    }

    private fun parameter(value: String, name: String): String? {
        val match = Regex("(?:^|;)\\s*${Regex.escape(name)}\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]*))", RegexOption.IGNORE_CASE).find(value)
        return match?.groupValues?.let { groups -> groups[1].ifBlank { groups[2] } }?.takeIf(String::isNotBlank)
    }
}

