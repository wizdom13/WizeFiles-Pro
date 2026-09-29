// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

class NearbyLanConnectionTest {
    @Test fun tlsPeersAgreeOnFreshQrProofAndRequireBothApprovals() {
        val sharedIdentity = NearbyTlsIdentity.create()
        Fixture(sharedIdentity).use { first ->
            assertArrayEquals(first.sender.authenticationToken, first.receiver.authenticationToken)
            assertTrue(NearbyQrAuthentication.matches(first.sender.authenticationToken, NearbyQrAuthentication.encode(first.receiver.authenticationToken)))
            val failed = CompletableFuture<Exception>()
            first.receiver.accept(first.receiveCallback)
            first.sender.send(NearbyPayload.fromBytes(byteArrayOf(1))).addOnFailureListener { failed.complete(it) }
            assertTrue(failed.get(5, TimeUnit.SECONDS).message!!.contains("approval"))
            assertEquals(2L, first.ready.count)
            Fixture(sharedIdentity).use { second ->
                assertFalse(MessageDigest.isEqual(first.sender.authenticationToken, second.sender.authenticationToken))
                assertFalse(NearbyQrAuthentication.matches(first.sender.authenticationToken, NearbyQrAuthentication.encode(second.receiver.authenticationToken)))
            }
            first.sender.accept(first.sendCallback)
            assertTrue(first.ready.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun metadataPrecedesMultiMegabyteStreamAndAuthenticatedEnd() {
        Fixture().use { f ->
            f.approve()
            val bytes = ByteArray(3 * 1024 * 1024 + 123) { (it * 31).toByte() }
            f.sender.send(NearbyPayload.fromBytes("FILE_BEGIN".toByteArray()))
            f.sender.send(NearbyPayload.fromStream(ByteArrayInputStream(bytes), bytes.size.toLong()))
            assertEquals("FILE_BEGIN", String(f.controls.poll(5, TimeUnit.SECONDS)!!))
            val payload = f.streams.poll(5, TimeUnit.SECONDS)!!
            assertEquals(bytes.size.toLong(), payload.length)
            val received = payload.asStream()!!.asInputStream().use { input ->
                readExactly(input, bytes.size).also { assertEquals(-1, input.read()) }
            }
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(bytes), MessageDigest.getInstance("SHA-256").digest(received))
            f.receiver.send(NearbyPayload.fromBytes("FILE_COMPLETE".toByteArray()))
            assertEquals("FILE_COMPLETE", String(f.reverseControls.poll(5, TimeUnit.SECONDS)!!))
            assertTrue(f.errors.isEmpty())
        }
    }

    @Test fun cancelUnblocksPipeAndNewStreamResumesFromAnOffset() {
        Fixture().use { f ->
            f.approve()
            val content = ByteArray(2 * 1024 * 1024) { (it xor (it ushr 9)).toByte() }
            val payload = NearbyPayload.fromStream(ByteArrayInputStream(content), content.size.toLong())
            f.sender.send(payload)
            val incoming = f.streams.poll(5, TimeUnit.SECONDS)!!
            val prefix = readExactly(incoming.asStream()!!.asInputStream(), 65_536)
            f.receiver.cancelPayload(incoming.id)
            assertNotNull(f.canceled.poll(5, TimeUnit.SECONDS))
            f.sender.send(NearbyPayload.fromBytes("RESUME_REQUEST".toByteArray()))
            f.sender.send(NearbyPayload.fromStream(ByteArrayInputStream(content, prefix.size, content.size - prefix.size), (content.size - prefix.size).toLong()))
            assertEquals("RESUME_REQUEST", String(f.controls.poll(5, TimeUnit.SECONDS)!!))
            val resumed = f.streams.poll(5, TimeUnit.SECONDS)!!
            val suffix = resumed.asStream()!!.asInputStream().use { readExactly(it, content.size - prefix.size) }
            assertArrayEquals(content, prefix + suffix)
            assertTrue(f.errors.toString(), f.errors.isEmpty())
        }
    }

    @Test fun immediateCancellationDoesNotStartAnOrphanedStreamOrBreakTheNextFile() {
        Fixture().use { f ->
            f.approve()
            repeat(5) {
                val payload = NearbyPayload.fromStream(ByteArrayInputStream(ByteArray(1024)), 1024)
                f.sender.send(payload)
                f.sender.cancelPayload(payload.id)
                f.sender.send(NearbyPayload.fromBytes("PAUSED".toByteArray()))
                assertEquals("PAUSED", String(f.controls.poll(5, TimeUnit.SECONDS)!!))
                while (true) {
                    val discarded = f.streams.poll() ?: break
                    discarded.asStream()?.asInputStream()?.close()
                }
            }
            val bytes = "resumed content".toByteArray()
            f.sender.send(NearbyPayload.fromStream(ByteArrayInputStream(bytes), bytes.size.toLong()))
            val resumed = f.streams.poll(5, TimeUnit.SECONDS)!!
            assertArrayEquals(bytes, resumed.asStream()!!.asInputStream().use { it.readBytes() })
            assertTrue(f.errors.toString(), f.errors.isEmpty())
        }
    }

    @Test fun unapprovedWirePayloadIsRejected() {
        Fixture().use { f ->
            DataOutputStream(f.clientSocket.outputStream).apply { writeByte(2); writeLong(10); writeInt(0); flush() }
            assertTrue(f.receiverErrors.poll(5, TimeUnit.SECONDS)!!.message.orEmpty().contains("approval"))
            assertTrue(f.controls.isEmpty())
        }
    }

    @Test fun oversizedWireFrameIsRejectedBeforeAllocation() {
        Fixture().use { f ->
            f.approve()
            DataOutputStream(f.clientSocket.outputStream).apply { writeByte(2); writeLong(10); writeInt(Int.MAX_VALUE); flush() }
            assertTrue(f.receiverErrors.poll(5, TimeUnit.SECONDS)!!.message.orEmpty().contains("length"))
            assertTrue(f.controls.isEmpty())
        }
    }

    @Test fun prematureSourceEofDisconnectsInsteadOfReportingCompletion() {
        Fixture().use { f ->
            f.approve()
            f.sender.send(NearbyPayload.fromStream(ByteArrayInputStream(byteArrayOf(1)), 5L * 1024 * 1024 * 1024))
            val payload = f.streams.poll(5, TimeUnit.SECONDS)!!
            assertEquals(5L * 1024 * 1024 * 1024, payload.length)
            assertNotNull(f.errors.poll(5, TimeUnit.SECONDS))
            assertTrue(f.completed.isEmpty())
        }
    }

    @Test fun zeroLengthStreamAndPeerDisconnectAreHandled() {
        Fixture().use { f ->
            f.approve()
            f.sender.send(NearbyPayload.fromStream(ByteArrayInputStream(ByteArray(0)), 0))
            val payload = f.streams.poll(5, TimeUnit.SECONDS)!!
            assertEquals(-1, payload.asStream()!!.asInputStream().use { it.read() })
            f.sender.close()
            assertNotNull(f.errors.poll(5, TimeUnit.SECONDS))
        }
    }

    private class Fixture(private val identity: NearbyTlsIdentity = NearbyTlsIdentity.create()) : Closeable {
        private val tasks = Executors.newCachedThreadPool()
        private val callbacks = Executors.newSingleThreadExecutor()
        @Volatile private var closing = false
        private val dispatch: (() -> Unit) -> Unit = { task ->
            if (!closing) try { callbacks.execute(task) } catch (error: java.util.concurrent.RejectedExecutionException) {
                if (!closing) throw error
            }
        }
        val ready = CountDownLatch(2)
        val errors = LinkedBlockingQueue<Exception>()
        val receiverErrors = LinkedBlockingQueue<Exception>()
        val controls = LinkedBlockingQueue<ByteArray>()
        val reverseControls = LinkedBlockingQueue<ByteArray>()
        val streams = LinkedBlockingQueue<NearbyPayload>()
        val canceled = LinkedBlockingQueue<Long>()
        val completed = LinkedBlockingQueue<Long>()
        private val listener = identity.context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
        val clientSocket = NearbyTlsIdentity.pairingContext().socketFactory.createSocket(InetAddress.getLoopbackAddress(), listener.localPort) as SSLSocket
        val sender: NearbyLanConnection
        val receiver: NearbyLanConnection
        val sendCallback = callback(reverseControls)
        val receiveCallback = callback(controls)

        init {
            val server = tasks.submit<NearbyLanConnection> {
                NearbyLanConnection.pair(listener.accept() as SSLSocket, true, "Receiver", identity.certificate.encoded,
                    dispatch, ready::countDown, { receiverErrors.offer(it); errors.offer(it) })
            }
            sender = NearbyLanConnection.pair(clientSocket, false, "Sender", null,
                dispatch, ready::countDown, { errors.offer(it) })
            receiver = server.get(10, TimeUnit.SECONDS)
            sender.start("receiver")
            receiver.start("sender")
        }
        fun approve() {
            receiver.accept(receiveCallback)
            sender.accept(sendCallback)
            assertTrue(ready.await(5, TimeUnit.SECONDS))
        }
        private fun callback(bytes: LinkedBlockingQueue<ByteArray>) = object : NearbyPayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: NearbyPayload) {
                if (payload.type == NearbyPayload.Type.BYTES) bytes.offer(payload.asBytes()) else streams.offer(payload)
            }
            override fun onPayloadTransferUpdate(endpointId: String, update: NearbyPayloadUpdate) {
                when (update.status) {
                    NearbyPayloadUpdate.Status.CANCELED -> canceled.offer(update.payloadId)
                    NearbyPayloadUpdate.Status.SUCCESS -> completed.offer(update.payloadId)
                    else -> Unit
                }
            }
        }
        override fun close() {
            closing = true
            sender.close()
            receiver.close()
            listener.close()
            tasks.shutdownNow()
            callbacks.shutdown()
            callbacks.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    companion object {
        private fun readExactly(input: InputStream, length: Int): ByteArray {
            val result = ByteArray(length)
            java.io.DataInputStream(input).readFully(result)
            return result
        }
    }
}
