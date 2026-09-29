// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.Manifest
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

@RunWith(AndroidJUnit4::class)
class NearbyLanRuntimeTest {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun platformTlsTransfersAfterQrApproval() {
        val executor = Executors.newCachedThreadPool()
        val ready = CountDownLatch(2)
        val received = CompletableFuture<NearbyPayload>()
        val failure = CompletableFuture<Exception>()
        val identity = NearbyTlsIdentity.create()
        val listener = identity.context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        val connections = mutableListOf<NearbyLanConnection>()
        try {
            val server = executor.submit<NearbyLanConnection> {
                NearbyLanConnection.pair(listener.accept() as SSLSocket, true, "Receiver", identity.certificate.encoded,
                    { it() }, ready::countDown, { failure.complete(it) })
            }
            val client = NearbyLanConnection.pair(
                NearbyTlsIdentity.pairingContext().socketFactory.createSocket("127.0.0.1", listener.localPort) as SSLSocket,
                false, "Sender", null, { it() }, ready::countDown, { failure.complete(it) })
            connections += client
            val receiver = server.get(15, TimeUnit.SECONDS)
            connections += receiver
            assertTrue(NearbyQrAuthentication.matches(client.authenticationToken, NearbyQrAuthentication.encode(receiver.authenticationToken)))
            val callback = object : NearbyPayloadCallback() {
                override fun onPayloadReceived(endpointId: String, payload: NearbyPayload) { received.complete(payload) }
                override fun onPayloadTransferUpdate(endpointId: String, update: NearbyPayloadUpdate) = Unit
            }
            receiver.start("sender")
            client.start("receiver")
            receiver.accept(callback)
            client.accept(callback)
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            val bytes = ByteArray(1024 * 1024 + 7) { (it * 37).toByte() }
            client.send(NearbyPayload.fromStream(ByteArrayInputStream(bytes), bytes.size.toLong()))
            val stream = received.get(10, TimeUnit.SECONDS).asStream()!!.asInputStream()
            val result = ByteArray(bytes.size)
            stream.use {
                java.io.DataInputStream(it).readFully(result)
                assertEquals(-1, it.read())
            }
            assertArrayEquals(bytes, result)
            assertFalse(if (failure.isDone) failure.get().toString() else "", failure.isDone)
        } finally {
            connections.forEach { it.close() }
            listener.close()
            executor.shutdownNow()
        }
    }

    @Test fun androidDiscoveryPairsTwoClientsAndSendsAnOffer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val receiver = NearbyLanClient(instrumentation.targetContext)
        val sender = NearbyLanClient(instrumentation.targetContext)
        val displayName = "Nearby test ${UUID.randomUUID().toString().take(8)}"
        val receiverPair = CompletableFuture<Pair<String, NearbyConnectionInfo>>()
        val senderPair = CompletableFuture<Pair<String, NearbyConnectionInfo>>()
        val ready = CountDownLatch(2)
        val received = CompletableFuture<ByteArray>()
        val started = AtomicBoolean()
        fun lifecycle(pair: CompletableFuture<Pair<String, NearbyConnectionInfo>>) = object : NearbyConnectionCallback() {
            override fun onConnectionInitiated(id: String, info: NearbyConnectionInfo) { pair.complete(id to info) }
            override fun onConnectionResult(id: String, resolution: NearbyConnectionResult) { if (resolution.accepted) ready.countDown() }
            override fun onDisconnected(id: String) { received.completeExceptionally(IllegalStateException("Disconnected")) }
        }
        val payloads = object : NearbyPayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: NearbyPayload) { received.complete(payload.asBytes()) }
            override fun onPayloadTransferUpdate(endpointId: String, update: NearbyPayloadUpdate) = Unit
        }
        try {
            instrumentation.runOnMainSync {
                receiver.startAdvertising(displayName, NEARBY_SERVICE_ID, lifecycle(receiverPair))
                    .addOnFailureListener { receiverPair.completeExceptionally(it) }
                sender.startDiscovery(NEARBY_SERVICE_ID, object : NearbyDiscoveryCallback() {
                    override fun onEndpointFound(id: String, info: NearbyDiscoveryInfo) {
                        if (info.endpointName == displayName && started.compareAndSet(false, true)) {
                            sender.requestConnection("Nearby test sender", id, lifecycle(senderPair))
                                .addOnFailureListener { senderPair.completeExceptionally(it) }
                        }
                    }
                    override fun onEndpointLost(id: String) = Unit
                }).addOnFailureListener { senderPair.completeExceptionally(it) }
            }
            val incoming = receiverPair.get(30, TimeUnit.SECONDS)
            val outgoing = senderPair.get(10, TimeUnit.SECONDS)
            assertTrue(NearbyQrAuthentication.matches(outgoing.second.rawAuthenticationToken,
                NearbyQrAuthentication.encode(incoming.second.rawAuthenticationToken)))
            instrumentation.runOnMainSync {
                receiver.acceptConnection(incoming.first, payloads)
                sender.acceptConnection(outgoing.first, payloads)
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            // Stopping discovery/advertising must preserve the approved channel.
            instrumentation.runOnMainSync {
                receiver.stopAdvertising()
                sender.stopDiscovery()
                sender.sendPayload(outgoing.first, NearbyPayload.fromBytes("OFFER".toByteArray()))
            }
            assertArrayEquals("OFFER".toByteArray(), received.get(10, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { sender.close(); receiver.close() }
        }
    }

    @Test fun scannerReleasesCameraAcrossBackgroundAndRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<NearbyQrScannerActivity>(Intent(context, NearbyQrScannerActivity::class.java)).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
