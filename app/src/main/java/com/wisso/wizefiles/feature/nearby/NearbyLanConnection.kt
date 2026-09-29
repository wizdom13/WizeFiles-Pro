// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

/** A bounded, framed channel over TLS. Payloads are forbidden until BOTH people approve pairing. */
internal class NearbyLanConnection private constructor(
    private val socket: SSLSocket,
    val peerName: String,
    val authenticationToken: ByteArray,
    private val dispatch: (() -> Unit) -> Unit,
    private val ready: () -> Unit,
    private val disconnected: (Exception) -> Unit
) : Closeable {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    private val closed = AtomicBoolean()
    private val callbackSlots = Semaphore(8)
    @Volatile private var readerThread: Thread? = null
    private val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(64)) { task ->
        Thread(task, "nearby-tls-writer").apply { isDaemon = true }
    }
    private val approvalLock = Any()
    private var localApproved = false
    private var remoteApproved = false
    @Volatile private var authenticated = false
    @Volatile private var callbacks: NearbyPayloadCallback? = null
    private val outgoing = ConcurrentHashMap<Long, Outgoing>()
    private val incoming = ConcurrentHashMap<Long, Incoming>()
    private val canceledIncoming = java.util.Collections.synchronizedSet(LinkedHashSet<Long>())
    private var endpointId = ""

    private class Outgoing(val payload: NearbyPayload) {
        val canceled = AtomicBoolean()
        var sent = 0L
    }
    private class Incoming(val id: Long, val length: Long) : Closeable {
        val source = PipedInputStream(CHUNK_SIZE * 2)
        val sink = PipedOutputStream(source)
        val payload = NearbyPayload.receivedStream(id, source, length)
        var received = 0L
        override fun close() {
            payload.markCanceled()
            runCatching { source.close() }
            runCatching { sink.close() }
        }
    }

    fun start(id: String) {
        endpointId = id
        readerThread = Thread({ readFrames() }, "nearby-tls-reader").apply { isDaemon = true; start() }
    }

    fun accept(callback: NearbyPayloadCallback): NearbyAction = NearbyAction(dispatch).also { action ->
        enqueue(action) {
            synchronized(approvalLock) {
                if (localApproved) throw IOException("Connection already approved")
                callbacks = callback
                frame(ACCEPT, 0, ByteArray(0))
                localApproved = true
                action.succeed()
                notifyReady()
            }
        }
    }

    fun send(payload: NearbyPayload): NearbyAction = NearbyAction(dispatch).also { action ->
        if (!authenticated || closed.get()) {
            action.fail(IOException("QR verification and approval are required"))
        } else if (payload.type == NearbyPayload.Type.BYTES) {
            enqueue(action) {
                frame(CONTROL, payload.id, requireNotNull(payload.asBytes()))
                action.succeed()
            }
        } else {
            val state = Outgoing(payload)
            synchronized(outgoing) {
                if (outgoing.isNotEmpty()) {
                    action.fail(IOException("Overlapping outgoing streams"))
                    return@also
                }
                // Register before queueing so a prompt pause can cancel an unopened stream.
                outgoing[payload.id] = state
            }
            enqueue(action) {
                if (!state.canceled.get()) {
                    frame(STREAM_BEGIN, payload.id, java.nio.ByteBuffer.allocate(8).putLong(payload.length).array())
                    pump(state)
                }
                action.succeed()
            }
        }
    }

    /** Each chunk yields the writer queue so PAUSE, ACK and CANCEL cannot be starved by a file. */
    private fun pump(state: Outgoing) {
        enqueue {
            if (state.canceled.get()) return@enqueue
            val payload = state.payload
            val remaining = payload.length - state.sent
            if (remaining == 0L) {
                frame(STREAM_END, payload.id, ByteArray(0))
                outgoing.remove(payload.id, state)
                runCatching { payload.asStream()?.asInputStream()?.close() }
                update(payload.id, payload.length, NearbyPayloadUpdate.Status.SUCCESS)
                return@enqueue
            }
            val bytes = ByteArray(minOf(CHUNK_SIZE.toLong(), remaining).toInt())
            val count = try {
                requireNotNull(payload.asStream()).asInputStream().read(bytes)
            } catch (error: IOException) {
                if (state.canceled.get()) return@enqueue else throw error
            }
            if (state.canceled.get()) return@enqueue
            if (count < 0) throw EOFException("Source file ended before its declared length")
            if (count > 0) {
                frame(STREAM_CHUNK, payload.id, if (count == bytes.size) bytes else bytes.copyOf(count))
                state.sent += count
            }
            pump(state)
        }
    }

    fun cancelPayload(id: Long) {
        cancelOutgoing(id)
        incoming.remove(id)?.let { stream ->
            rememberCanceled(id)
            stream.close()
        }
        enqueue { frame(STREAM_CANCEL, id, ByteArray(0)) }
    }

    private fun cancelOutgoing(id: Long) {
        outgoing.remove(id)?.let { state ->
            state.canceled.set(true)
            runCatching { state.payload.asStream()?.asInputStream()?.close() }
            update(id, state.sent, NearbyPayloadUpdate.Status.CANCELED)
        }
    }

    private fun rememberCanceled(id: Long) = synchronized(canceledIncoming) {
        if (canceledIncoming.size >= 16) canceledIncoming.remove(canceledIncoming.first())
        canceledIncoming.add(id)
    }

    private fun notifyReady() {
        if (localApproved && remoteApproved && !authenticated) {
            socket.soTimeout = 0
            authenticated = true
            dispatch(ready)
        }
    }

    private fun readFrames() {
        try {
            while (!closed.get()) {
                val kind = input.readUnsignedByte()
                val id = input.readLong()
                val length = input.readInt()
                val maximum = when (kind) {
                    CONTROL -> MAX_CONTROL
                    STREAM_CHUNK -> CHUNK_SIZE
                    STREAM_BEGIN -> 8
                    ACCEPT, STREAM_END, STREAM_CANCEL -> 0
                    else -> throw IOException("Unknown transport frame")
                }
                if (length !in 0..maximum) throw IOException("Invalid frame length")
                if (kind == ACCEPT) {
                    if (id != 0L) throw IOException("Invalid approval frame")
                    synchronized(approvalLock) {
                        if (remoteApproved) throw IOException("Duplicate approval")
                        remoteApproved = true
                        notifyReady()
                    }
                    continue
                }
                if (!authenticated) throw IOException("Payload before mutual approval")
                if (id <= 0) throw IOException("Invalid payload identifier")
                val bytes = ByteArray(length)
                input.readFully(bytes)
                when (kind) {
                    CONTROL -> deliver(NearbyPayload.receivedBytes(id, bytes))
                    STREAM_BEGIN -> {
                        if (length != 8 || incoming.isNotEmpty()) throw IOException("Overlapping or malformed stream")
                        val size = java.nio.ByteBuffer.wrap(bytes).long
                        if (size < 0 || canceledIncoming.contains(id)) throw IOException("Invalid stream length or identifier")
                        val stream = Incoming(id, size)
                        incoming[id] = stream
                        deliver(stream.payload)
                    }
                    STREAM_CHUNK -> {
                        val stream = incoming[id]
                        if (stream == null) {
                            if (!canceledIncoming.contains(id)) throw IOException("Chunk without stream")
                        } else {
                            if (length == 0 || length.toLong() > stream.length - stream.received) throw IOException("Stream exceeded declared length")
                            try {
                                stream.sink.write(bytes)
                                stream.sink.flush()
                                stream.received += length
                            } catch (error: IOException) {
                                if (!canceledIncoming.contains(id)) throw error
                            }
                        }
                    }
                    STREAM_END -> {
                        val stream = incoming.remove(id)
                        if (stream != null) {
                            if (stream.received != stream.length) {
                                stream.close()
                                throw IOException("Truncated stream")
                            }
                            stream.sink.close()
                            update(id, stream.length, NearbyPayloadUpdate.Status.SUCCESS)
                        } else if (!canceledIncoming.remove(id)) throw IOException("End without stream")
                    }
                    STREAM_CANCEL -> {
                        cancelOutgoing(id)
                        incoming.remove(id)?.let { rememberCanceled(id); it.close() }
                    }
                }
            }
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun deliver(payload: NearbyPayload) {
        // A verified peer still cannot enqueue unbounded megabyte-sized messages on the UI thread.
        callbackSlots.acquire()
        if (closed.get()) { callbackSlots.release(); return }
        dispatch {
            try { callbacks?.onPayloadReceived(endpointId, payload) }
            finally { callbackSlots.release() }
        }
    }

    private fun update(id: Long, bytes: Long, status: NearbyPayloadUpdate.Status) = dispatch {
        callbacks?.onPayloadTransferUpdate(endpointId, NearbyPayloadUpdate(id, bytes, status))
    }

    private fun frame(kind: Int, id: Long, bytes: ByteArray) {
        require(bytes.size <= if (kind == STREAM_CHUNK) CHUNK_SIZE else MAX_CONTROL)
        output.writeByte(kind)
        output.writeLong(id)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun enqueue(action: NearbyAction? = null, operation: () -> Unit) {
        if (closed.get()) { action?.fail(IOException("Connection closed")); return }
        try {
            writer.execute {
                try { operation() } catch (error: Exception) { action?.fail(error); fail(error) }
            }
        } catch (error: java.util.concurrent.RejectedExecutionException) {
            action?.fail(error)
            fail(error)
        }
    }

    private fun fail(error: Exception) {
        if (shutdown()) dispatch { disconnected(error) }
    }

    override fun close() { shutdown() }
    private fun shutdown(): Boolean {
        if (!closed.compareAndSet(false, true)) return false
        readerThread?.interrupt()
        runCatching { socket.close() }
        incoming.values.forEach { it.close() }
        incoming.clear()
        outgoing.keys.toList().forEach(::cancelOutgoing)
        writer.shutdownNow()
        authenticationToken.fill(0)
        return true
    }

    companion object {
        private const val ACCEPT = 1
        private const val CONTROL = 2
        private const val STREAM_BEGIN = 3
        private const val STREAM_CHUNK = 4
        private const val STREAM_END = 5
        private const val STREAM_CANCEL = 6
        private const val CHUNK_SIZE = 65_536
        private const val MAX_CONTROL = 1_000_000
        private const val MAGIC = 0x575a4c32 // WZL2; deliberately incompatible with Google Nearby.

        /** Complete TLS and a fresh encrypted nonce exchange before asking either person to approve. */
        fun pair(
            socket: SSLSocket,
            server: Boolean,
            ownName: String,
            serverCertificate: ByteArray? = null,
            dispatch: (() -> Unit) -> Unit,
            ready: () -> Unit,
            disconnected: (Exception) -> Unit
        ): NearbyLanConnection {
            try {
                NearbyTlsIdentity.configure(socket)
                socket.startHandshake()
                val output = DataOutputStream(socket.outputStream)
                val input = DataInputStream(socket.inputStream)
                val nonce = ByteArray(32).also(SecureRandom()::nextBytes)
                val name = ownName.take(64).toByteArray(Charsets.UTF_8)
                output.writeInt(MAGIC)
                output.write(nonce)
                output.writeInt(name.size)
                output.write(name)
                output.flush()
                if (input.readInt() != MAGIC) throw IOException("Incompatible transfer protocol")
                val peerNonce = ByteArray(32).also(input::readFully)
                val nameLength = input.readInt()
                if (nameLength !in 1..256) throw IOException("Invalid peer name")
                val peerName = ByteArray(nameLength).also(input::readFully).toString(Charsets.UTF_8)
                val certificate = if (server) requireNotNull(serverCertificate) else socket.session.peerCertificates[0].encoded
                val proof = NearbyTlsIdentity.proof(certificate, if (server) peerNonce else nonce, if (server) nonce else peerNonce)
                socket.soTimeout = 75_000
                return NearbyLanConnection(socket, peerName, proof, dispatch, ready, disconnected)
            } catch (error: Exception) {
                runCatching { socket.close() }
                throw error
            }
        }
    }
}
