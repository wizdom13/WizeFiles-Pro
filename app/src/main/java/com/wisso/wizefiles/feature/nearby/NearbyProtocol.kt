package com.wisso.wizefiles.feature.nearby

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import com.wisso.wizefiles.storage.SecureRelativePath

internal const val NEARBY_PROTOCOL_VERSION = 1
internal const val NEARBY_SERVICE_ID = "com.wisso.wizefiles.nearby.v1"
internal const val NEARBY_DISCOVERY_MILLIS = 120_000L
internal const val NEARBY_OFFER_MILLIS = 60_000L
internal const val NEARBY_ACK_BYTES = 4L * 1024L * 1024L

internal enum class NearbyRole { SEND, RECEIVE }
internal enum class NearbyConflictPolicy { REPLACE, KEEP_BOTH, SKIP }

internal data class NearbyManifestEntry(
    val id: String,
    val relativePath: String,
    val sourceUri: String = "",
    val directory: Boolean,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val fingerprint: String
)

internal data class NearbyOffer(
    val sessionId: String = UUID.randomUUID().toString(),
    val operationId: String,
    val entries: List<NearbyManifestEntry>,
    val totalBytes: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    init {
        require(entries.isNotEmpty()) { "A nearby offer must contain at least one item" }
        require(entries.size <= NearbyPathSecurity.MAX_ITEMS) { "Too many items" }
        require(totalBytes >= 0) { "Invalid total size" }
        entries.forEach {
            require(it.id.length in 1..128) { "Invalid item ID" }
            NearbyPathSecurity.requireSafeRelativePath(it.relativePath)
        }
        require(entries.map(NearbyManifestEntry::id).toSet().size == entries.size) {
            "Duplicate item IDs are forbidden"
        }
        require(entries.map(NearbyManifestEntry::relativePath).toSet().size == entries.size) {
            "Duplicate destination paths are forbidden"
        }
    }
}

internal data class NearbyMessage(
    val type: String,
    val sessionId: String,
    val body: JSONObject
)

internal object NearbyProtocol {
    private const val MAX_WIRE_BYTES = 1_000_000
    private const val MAX_DECODED_BYTES = 4 * 1024 * 1024
    private val GZIP_MAGIC = byteArrayOf('W'.code.toByte(), 'Z'.code.toByte(), 'G'.code.toByte(), 1)

    fun hello(sessionId: String, role: NearbyRole, resumable: Boolean): ByteArray = message(
        "HELLO", sessionId, JSONObject()
            .put("protocol", NEARBY_PROTOCOL_VERSION)
            .put("role", role.name)
            .put("resumable", resumable)
    )

    fun offer(offer: NearbyOffer): ByteArray {
        val entries = JSONArray()
        offer.entries.forEach { entry ->
            entries.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("path", entry.relativePath)
                    .put("directory", entry.directory)
                    .put("size", entry.sizeBytes)
                    .put("modified", entry.modifiedMillis)
                    .put("fingerprint", entry.fingerprint)
            )
        }
        return message(
            "OFFER",
            offer.sessionId,
            JSONObject()
                .put("operationId", offer.operationId)
                .put("created", offer.createdAtMillis)
                .put("totalBytes", offer.totalBytes)
                .put("entries", entries)
        )
    }

    fun offerAccepted(
        sessionId: String,
        destinationUri: String,
        policy: NearbyConflictPolicy,
        offsets: Map<String, Long>
    ): ByteArray {
        val saved = JSONObject()
        offsets.forEach { (id, offset) -> saved.put(id, offset) }
        return message(
            "OFFER_ACCEPTED", sessionId, JSONObject()
                .put("destination", destinationUri)
                .put("conflictPolicy", policy.name)
                .put("offsets", saved)
        )
    }

    fun rejected(sessionId: String, reason: String): ByteArray = message(
        "OFFER_REJECTED", sessionId, JSONObject().put("reason", reason.take(256))
    )

    fun fileBegin(
        sessionId: String,
        itemId: String,
        payloadId: Long,
        offset: Long,
        length: Long
    ): ByteArray = message(
        "FILE_BEGIN", sessionId, JSONObject()
            .put("itemId", itemId)
            .put("payloadId", payloadId)
            .put("offset", offset)
            .put("length", length)
    )

    fun progress(sessionId: String, itemId: String, offset: Long): ByteArray = message(
        "PROGRESS_ACK", sessionId, JSONObject().put("itemId", itemId).put("offset", offset)
    )

    fun fileComplete(sessionId: String, itemId: String, size: Long): ByteArray = message(
        "FILE_COMPLETE", sessionId, JSONObject().put("itemId", itemId).put("size", size)
    )

    fun command(type: String, sessionId: String): ByteArray = message(type, sessionId, JSONObject())

    fun decode(bytes: ByteArray): NearbyMessage {
        require(bytes.size in 1..MAX_WIRE_BYTES) { "Invalid nearby control message size" }
        val decoded = if (bytes.size >= GZIP_MAGIC.size &&
            bytes.copyOfRange(0, GZIP_MAGIC.size).contentEquals(GZIP_MAGIC)
        ) {
            GZIPInputStream(ByteArrayInputStream(bytes, GZIP_MAGIC.size, bytes.size - GZIP_MAGIC.size))
                .use { input -> readLimited(input, MAX_DECODED_BYTES) }
        } else {
            bytes
        }
        val root = JSONObject(String(decoded, StandardCharsets.UTF_8))
        require(root.optInt("protocol", -1) == NEARBY_PROTOCOL_VERSION) {
            "Unsupported nearby protocol"
        }
        val type = root.getString("type")
        val sessionId = root.getString("sessionId")
        require(sessionId.length in 8..128) { "Invalid session identifier" }
        return NearbyMessage(type, sessionId, root.optJSONObject("body") ?: JSONObject())
    }

    fun decodeOffer(message: NearbyMessage): NearbyOffer {
        require(message.type == "OFFER")
        val array = message.body.getJSONArray("entries")
        require(array.length() in 1..NearbyPathSecurity.MAX_ITEMS) { "Invalid manifest size" }
        val entries = ArrayList<NearbyManifestEntry>(array.length())
        var computedBytes = 0L
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            val relativePath = NearbyPathSecurity.requireSafeRelativePath(item.getString("path"))
            val size = item.getLong("size")
            require(size >= 0) { "Invalid file size" }
            computedBytes = Math.addExact(computedBytes, size)
            entries += NearbyManifestEntry(
                id = item.getString("id").also { require(it.length in 1..128) },
                relativePath = relativePath,
                directory = item.getBoolean("directory"),
                sizeBytes = size,
                modifiedMillis = item.optLong("modified", 0).coerceAtLeast(0),
                fingerprint = item.optString("fingerprint").take(256)
            )
        }
        val declared = message.body.getLong("totalBytes")
        require(declared == computedBytes) { "Manifest size does not match its entries" }
        return NearbyOffer(
            sessionId = message.sessionId,
            operationId = message.body.getString("operationId"),
            entries = entries,
            totalBytes = declared,
            createdAtMillis = message.body.optLong("created", System.currentTimeMillis())
        )
    }

    private fun message(type: String, sessionId: String, body: JSONObject): ByteArray {
        val encoded = JSONObject()
            .put("protocol", NEARBY_PROTOCOL_VERSION)
            .put("type", type)
            .put("sessionId", sessionId)
            .put("body", body)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= MAX_DECODED_BYTES) { "Nearby control message is too large" }
        if (encoded.size <= MAX_WIRE_BYTES) return encoded
        val compressed = ByteArrayOutputStream().also { output ->
            output.write(GZIP_MAGIC)
            GZIPOutputStream(output).use { it.write(encoded) }
        }.toByteArray()
        require(compressed.size <= MAX_WIRE_BYTES) { "Nearby manifest is too large" }
        return compressed
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Expanded nearby message is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

internal object NearbyPathSecurity {
    const val MAX_ITEMS = 100_000
    private const val MAX_PATH_BYTES = 1024
    private const val MAX_SEGMENT_LENGTH = 255
    private const val MAX_DEPTH = 64

    fun requireSafeRelativePath(value: String): String {
        require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_PATH_BYTES) { "Path is too long" }
        val segments = value.split('/')
        require(segments.size <= MAX_DEPTH) { "Path is too deep" }
        require(segments.all { it.length <= MAX_SEGMENT_LENGTH }) { "Path segment is too long" }
        return SecureRelativePath.validate(value)
    }

    fun resolveInside(root: Path, relativePath: String): Path {
        val safe = requireSafeRelativePath(relativePath)
        val normalizedRoot = root.toAbsolutePath().normalize()
        val result = safe.split('/').fold(normalizedRoot) { parent, segment -> parent.resolve(segment) }
            .normalize()
        return requireSafeDestination(normalizedRoot, result)
    }

    fun requireSafeDestination(root: Path, target: Path): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedTarget = target.toAbsolutePath().normalize()
        require(normalizedTarget.startsWith(normalizedRoot)) {
            "Destination escaped the accepted folder"
        }
        requireNotSymbolicLink(normalizedRoot)
        var current = normalizedRoot
        normalizedRoot.relativize(normalizedTarget).forEach { segment ->
            current = current.resolve(segment)
            requireNotSymbolicLink(current)
        }
        return normalizedTarget
    }

    private fun requireNotSymbolicLink(path: Path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(path)) {
                "Symbolic links are forbidden in the destination path"
            }
        }
    }
}
