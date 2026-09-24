package com.wisso.wizefiles.vault

import android.content.Context
import android.util.AtomicFile
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONObject

internal class VaultOpenSessionEncryptedStore private constructor(
    private val context: Context,
    val session: VaultOpenSession,
    key: ByteArray,
    private var logicalSize: Long,
    private var pendingRecovery: Boolean
) : Closeable {
    private val key = key.copyOf()
    private var closed = false

    private val sessionDir: File
        get() = sessionDirectory(context, session.sessionId)

    private val chunksDir: File
        get() = File(sessionDir, CHUNKS_DIRECTORY_NAME).apply { mkdirs() }

    @Synchronized
    fun size(): Long {
        ensureOpen()
        refreshState()
        return logicalSize
    }

    @Synchronized
    fun read(offset: Long, requestedSize: Int, destination: ByteArray): Int {
        ensureOpen()
        refreshState()
        require(offset >= 0L) { "Negative read offset" }
        if (offset >= logicalSize || requestedSize <= 0 || destination.isEmpty()) {
            return 0
        }
        val bytesToRead = minOf(
            requestedSize.toLong(),
            destination.size.toLong(),
            logicalSize - offset
        ).toInt()
        var position = offset
        var destinationOffset = 0
        while (destinationOffset < bytesToRead) {
            val chunkIndex = position / CHUNK_SIZE_BYTES
            val offsetInChunk = (position % CHUNK_SIZE_BYTES).toInt()
            val copySize = minOf(bytesToRead - destinationOffset, CHUNK_SIZE_BYTES - offsetInChunk)
            val chunk = readChunk(chunkIndex)
            try {
                System.arraycopy(chunk, offsetInChunk, destination, destinationOffset, copySize)
            } finally {
                VaultCrypto.zero(chunk)
            }
            position += copySize
            destinationOffset += copySize
        }
        return bytesToRead
    }

    @Synchronized
    fun write(offset: Long, requestedSize: Int, source: ByteArray): Int =
        write(offset, source, 0, minOf(requestedSize, source.size))

    @Synchronized
    fun truncate(newSize: Long) {
        ensureOpen()
        refreshState()
        require(newSize >= 0L) { "Negative Vault session size" }

        if (newSize == 0L) {
            chunksDir.listFiles().orEmpty().forEach { AtomicFile(it).delete() }
        } else if (newSize < logicalSize) {
            val lastChunkIndex = (newSize - 1L) / CHUNK_SIZE_BYTES
            chunksDir.listFiles().orEmpty().forEach { file ->
                val chunkIndex = file.name.removeSuffix(CHUNK_FILE_SUFFIX).toLongOrNull(16)
                if (chunkIndex != null && chunkIndex > lastChunkIndex) {
                    AtomicFile(file).delete()
                }
            }
            val offsetInLastChunk = (newSize % CHUNK_SIZE_BYTES).toInt()
            if (offsetInLastChunk != 0) {
                val chunk = readChunk(lastChunkIndex)
                try {
                    chunk.fill(0, offsetInLastChunk, chunk.size)
                    writeChunk(lastChunkIndex, chunk)
                } finally {
                    VaultCrypto.zero(chunk)
                }
            }
        }

        logicalSize = newSize
        writeMetadata()
    }

    @Synchronized
    fun markPendingRecovery() {
        ensureOpen()
        pendingRecovery = true
        writeMetadata()
    }

    fun openReplacingOutputStream(): OutputStream {
        truncate(0L)
        return object : OutputStream() {
            private val chunk = ByteArray(CHUNK_SIZE_BYTES)
            private var chunkIndex = 0L
            private var chunkSize = 0
            private var streamClosed = false

            override fun write(value: Int) {
                val oneByte = byteArrayOf(value.toByte())
                try {
                    write(oneByte, 0, 1)
                } finally {
                    VaultCrypto.zero(oneByte)
                }
            }

            override fun write(source: ByteArray, offset: Int, length: Int) {
                check(!streamClosed) { "Vault session output is closed" }
                require(offset >= 0 && length >= 0 && offset + length <= source.size)
                var sourceOffset = offset
                var remaining = length
                while (remaining > 0) {
                    val copySize = minOf(remaining, chunk.size - chunkSize)
                    System.arraycopy(source, sourceOffset, chunk, chunkSize, copySize)
                    chunkSize += copySize
                    sourceOffset += copySize
                    remaining -= copySize
                    if (chunkSize == chunk.size) {
                        flushChunk()
                    }
                }
            }

            override fun close() {
                if (streamClosed) return
                streamClosed = true
                try {
                    if (chunkSize > 0) {
                        flushChunk()
                    }
                } finally {
                    VaultCrypto.zero(chunk)
                }
            }

            private fun flushChunk() {
                synchronized(this@VaultOpenSessionEncryptedStore) {
                    ensureOpen()
                    if (chunkSize < chunk.size) {
                        chunk.fill(0, chunkSize, chunk.size)
                    }
                    writeChunk(chunkIndex, chunk)
                    logicalSize += chunkSize
                    writeMetadata()
                }
                chunk.fill(0)
                chunkIndex += 1L
                chunkSize = 0
            }
        }
    }

    fun openInputStream(): InputStream {
        val expectedSize = size()
        return object : InputStream() {
            private var position = 0L
            private val oneByte = ByteArray(1)

            override fun read(): Int {
                val count = read(oneByte, 0, 1)
                return if (count == -1) -1 else oneByte[0].toInt() and 0xff
            }

            override fun read(destination: ByteArray, offset: Int, length: Int): Int {
                require(offset >= 0 && length >= 0 && offset + length <= destination.size)
                if (position >= expectedSize) return -1
                val requested = minOf(length.toLong(), expectedSize - position).toInt()
                if (requested == 0) return 0
                val temporary = if (offset == 0 && requested <= destination.size) {
                    destination
                } else {
                    ByteArray(requested)
                }
                val count = this@VaultOpenSessionEncryptedStore.read(position, requested, temporary)
                if (temporary !== destination) {
                    try {
                        System.arraycopy(temporary, 0, destination, offset, count)
                    } finally {
                        VaultCrypto.zero(temporary)
                    }
                }
                position += count
                return count
            }

            override fun close() {
                VaultCrypto.zero(oneByte)
            }
        }
    }

    @Synchronized
    fun delete() {
        if (!closed) {
            close()
        }
        runCatching { sessionDir.deleteRecursively() }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        VaultCrypto.zero(key)
    }

    @Synchronized
    private fun write(offset: Long, source: ByteArray, sourceOffset: Int, length: Int): Int {
        ensureOpen()
        refreshState()
        require(offset >= 0L) { "Negative write offset" }
        require(sourceOffset >= 0 && length >= 0 && sourceOffset + length <= source.size)
        if (length == 0) return 0
        val writeEnd = offset + length.toLong()
        if (writeEnd < offset) throw IOException("Vault session file is too large")

        var position = offset
        var currentSourceOffset = sourceOffset
        var remaining = length
        while (remaining > 0) {
            val chunkIndex = position / CHUNK_SIZE_BYTES
            val offsetInChunk = (position % CHUNK_SIZE_BYTES).toInt()
            val copySize = minOf(remaining, CHUNK_SIZE_BYTES - offsetInChunk)
            val chunk = readChunk(chunkIndex)
            try {
                System.arraycopy(source, currentSourceOffset, chunk, offsetInChunk, copySize)
                writeChunk(chunkIndex, chunk)
            } finally {
                VaultCrypto.zero(chunk)
            }
            position += copySize
            currentSourceOffset += copySize
            remaining -= copySize
        }
        if (writeEnd > logicalSize) logicalSize = writeEnd
        writeMetadata()
        return length
    }

    private fun readChunk(chunkIndex: Long): ByteArray {
        val file = chunkFile(chunkIndex)
        if (!file.exists() && !File(file.path + ".bak").exists()) {
            return ByteArray(CHUNK_SIZE_BYTES)
        }
        val encoded = AtomicFile(file).openRead().use { it.readBytes() }
        return try {
            val payload = VaultCrypto.EncryptedPayload.decode(encoded)
            val plaintext = VaultCrypto.decryptAesGcm(payload, key, chunkAad(chunkIndex))
            if (plaintext.size != CHUNK_SIZE_BYTES) {
                VaultCrypto.zero(plaintext)
                throw IOException("Invalid encrypted Vault session chunk")
            }
            plaintext
        } finally {
            VaultCrypto.zero(encoded)
        }
    }

    private fun writeChunk(chunkIndex: Long, plaintext: ByteArray) {
        val payload = VaultCrypto.encryptAesGcm(plaintext, key, chunkAad(chunkIndex))
        val encoded = payload.encode()
        try {
            writeAtomicBytes(chunkFile(chunkIndex), encoded)
        } finally {
            VaultCrypto.zero(payload.iv)
            VaultCrypto.zero(payload.ciphertext)
            VaultCrypto.zero(encoded)
        }
    }

    private fun chunkFile(chunkIndex: Long): File =
        File(chunksDir, chunkIndex.toString(16).padStart(CHUNK_INDEX_WIDTH, '0') + CHUNK_FILE_SUFFIX)

    private fun chunkAad(chunkIndex: Long): ByteArray =
        "${session.sessionId}:$chunkIndex".toByteArray(Charsets.UTF_8)

    private fun writeMetadata() {
        val json = session.toJson(logicalSize, pendingRecovery).toString().toByteArray(Charsets.UTF_8)
        try {
            writeAtomicBytes(File(sessionDir, METADATA_FILE_NAME), json)
        } finally {
            VaultCrypto.zero(json)
        }
    }

    private fun ensureOpen() {
        check(!closed) { "Vault session store is closed" }
    }

    private fun refreshState() {
        val metadata = readMetadata(context, session.sessionId) ?: return
        logicalSize = metadata.getLong("size")
        pendingRecovery = metadata.optBoolean("pendingRecovery", false)
    }

    companion object {
        internal const val CHUNK_SIZE_BYTES = 64 * 1024
        private const val CHUNK_INDEX_WIDTH = 16
        private const val STORE_DIRECTORY_NAME = "vault_open_stores"
        private const val CHUNKS_DIRECTORY_NAME = "chunks"
        private const val CHUNK_FILE_SUFFIX = ".bin"
        private const val METADATA_FILE_NAME = "metadata.json"
        private const val STALE_STORE_MILLIS: Long = 24L * 60L * 60L * 1000L

        fun create(
            context: Context,
            session: VaultOpenSession,
            key: ByteArray
        ): VaultOpenSessionEncryptedStore {
            val directory = sessionDirectory(context, session.sessionId)
            directory.deleteRecursively()
            File(directory, CHUNKS_DIRECTORY_NAME).mkdirs()
            return VaultOpenSessionEncryptedStore(
                context,
                session,
                key,
                logicalSize = 0L,
                pendingRecovery = false
            ).also { it.writeMetadata() }
        }

        fun open(
            context: Context,
            session: VaultOpenSession,
            key: ByteArray
        ): VaultOpenSessionEncryptedStore? {
            val metadata = readMetadata(context, session.sessionId) ?: return null
            if (
                metadata.getString("vaultId") != session.vaultId ||
                metadata.getString("entryId") != session.entryId
            ) {
                throw IOException("Vault session metadata does not match the requested entry")
            }
            return VaultOpenSessionEncryptedStore(
                context,
                session,
                key,
                logicalSize = metadata.getLong("size"),
                pendingRecovery = metadata.optBoolean("pendingRecovery", false)
            )
        }

        fun pendingSessions(context: Context, vaultId: String): List<VaultOpenSession> =
            storeRoot(context).listFiles().orEmpty().mapNotNull { directory ->
                readMetadata(context, directory.name)?.takeIf {
                    it.optBoolean("pendingRecovery", false) && it.optString("vaultId") == vaultId
                }?.toSession()
            }

        fun delete(context: Context, sessionId: String) {
            runCatching { sessionDirectory(context, sessionId).deleteRecursively() }
        }

        fun cleanupStale(context: Context) {
            val cutoff = System.currentTimeMillis() - STALE_STORE_MILLIS
            storeRoot(context).listFiles().orEmpty().forEach { directory ->
                if (directory.isDirectory && directory.lastModified() < cutoff) {
                    if (!VaultOpenSessionAccess.isActive(directory.name)) {
                        runCatching { directory.deleteRecursively() }
                    }
                }
            }
        }

        private fun storeRoot(context: Context): File =
            File(context.noBackupFilesDir, STORE_DIRECTORY_NAME).apply { mkdirs() }

        private fun sessionDirectory(context: Context, sessionId: String): File =
            File(storeRoot(context), sessionId)

        internal fun readMetadata(context: Context, sessionId: String): JSONObject? {
            val file = File(sessionDirectory(context, sessionId), METADATA_FILE_NAME)
            return runCatching {
                val bytes = AtomicFile(file).openRead().use { it.readBytes() }
                try {
                    JSONObject(String(bytes, Charsets.UTF_8))
                } finally {
                    VaultCrypto.zero(bytes)
                }
            }.getOrNull()
        }
    }
}

internal object VaultOpenSessionAccess {
    data class ReleaseResult(val isLast: Boolean, val isDirty: Boolean)

    private data class State(
        var openCount: Int = 0,
        var dirty: Boolean = false,
        var finalizing: Boolean = false
    )

    private val locks = HashMap<String, Any>()
    private val states = HashMap<String, State>()

    fun <T> withLock(sessionId: String, block: () -> T): T {
        val lock = synchronized(this) { locks.getOrPut(sessionId) { Any() } }
        return synchronized(lock, block)
    }

    @Synchronized
    fun opened(sessionId: String, dirty: Boolean) {
        val state = states.getOrPut(sessionId) { State() }
        state.openCount += 1
        state.dirty = state.dirty || dirty
    }

    @Synchronized
    fun markDirty(sessionId: String) {
        states.getOrPut(sessionId) { State() }.dirty = true
    }

    @Synchronized
    fun released(sessionId: String): ReleaseResult {
        val state = states[sessionId] ?: return ReleaseResult(isLast = true, isDirty = false)
        state.openCount = (state.openCount - 1).coerceAtLeast(0)
        if (state.openCount > 0) return ReleaseResult(isLast = false, isDirty = state.dirty)
        state.finalizing = true
        return ReleaseResult(isLast = true, isDirty = state.dirty)
    }

    @Synchronized
    fun finished(sessionId: String) {
        states.remove(sessionId)
    }

    @Synchronized
    fun isActive(sessionId: String): Boolean = states[sessionId]?.let {
        it.openCount > 0 || it.finalizing
    } == true
}

private fun VaultOpenSession.toJson(size: Long, pendingRecovery: Boolean): JSONObject =
    JSONObject()
        .put("sessionId", sessionId)
        .put("vaultId", vaultId)
        .put("entryId", entryId)
        .put("displayName", displayName)
        .put("mimeType", mimeType.value)
        .put("originalSize", originalSize)
        .put("originalModifiedAt", originalModifiedAt)
        .put("openedAt", openedAt)
        .put("size", size)
        .put("pendingRecovery", pendingRecovery)

private fun JSONObject.toSession(): VaultOpenSession = VaultOpenSession(
    sessionId = getString("sessionId"),
    vaultId = getString("vaultId"),
    entryId = getString("entryId"),
    displayName = getString("displayName"),
    mimeType = com.wisso.wizefiles.core.files.mime.MimeType(getString("mimeType")),
    originalSize = getLong("originalSize"),
    originalModifiedAt = getLong("originalModifiedAt"),
    openedAt = getLong("openedAt"),
    pendingRecovery = optBoolean("pendingRecovery", false)
)

private fun writeAtomicBytes(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val atomicFile = AtomicFile(file)
    val output = atomicFile.startWrite()
    try {
        output.write(bytes)
        atomicFile.finishWrite(output)
    } catch (throwable: Throwable) {
        atomicFile.failWrite(output)
        throw throwable
    }
}
