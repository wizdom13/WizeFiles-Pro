// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/** Application-owned transport types. No Google service or downloadable module is involved. */
internal data class NearbyConnectionInfo(val endpointName: String, val rawAuthenticationToken: ByteArray)
internal data class NearbyConnectionResult(val accepted: Boolean)
internal data class NearbyDiscoveryInfo(val serviceId: String, val endpointName: String)

internal abstract class NearbyConnectionCallback {
    abstract fun onConnectionInitiated(id: String, info: NearbyConnectionInfo)
    abstract fun onConnectionResult(id: String, resolution: NearbyConnectionResult)
    abstract fun onDisconnected(id: String)
}

internal abstract class NearbyDiscoveryCallback {
    abstract fun onEndpointFound(id: String, info: NearbyDiscoveryInfo)
    abstract fun onEndpointLost(id: String)
}

internal abstract class NearbyPayloadCallback {
    abstract fun onPayloadReceived(endpointId: String, payload: NearbyPayload)
    abstract fun onPayloadTransferUpdate(endpointId: String, update: NearbyPayloadUpdate)
}

internal data class NearbyPayloadUpdate(val payloadId: Long, val totalBytes: Long, val status: Status) {
    enum class Status { SUCCESS, CANCELED, FAILURE }
}

internal class NearbyPayload private constructor(
    val id: Long,
    val type: Type,
    private val bytes: ByteArray?,
    private val stream: InputStream?,
    val length: Long
) {
    @Volatile var isCanceled: Boolean = false
        private set
    fun markCanceled() { isCanceled = true }
    enum class Type { BYTES, STREAM }
    fun asBytes(): ByteArray? = bytes
    fun asStream(): Stream? = stream?.let(::Stream)
    class Stream(private val input: InputStream) {
        fun asInputStream(): InputStream = input
    }
    companion object {
        private val ids = AtomicLong(SecureRandom().nextLong().ushr(1).coerceAtLeast(1))
        private fun nextId(): Long = ids.updateAndGet { if (it == Long.MAX_VALUE) 1 else it + 1 }
        fun fromBytes(bytes: ByteArray) = NearbyPayload(nextId(), Type.BYTES, bytes, null, bytes.size.toLong())
        fun fromStream(stream: InputStream, length: Long): NearbyPayload {
            require(length >= 0)
            return receivedStream(nextId(), stream, length)
        }
        fun receivedBytes(id: Long, bytes: ByteArray) = NearbyPayload(id, Type.BYTES, bytes, null, bytes.size.toLong())
        fun receivedStream(id: Long, stream: InputStream, length: Long) =
            NearbyPayload(id, Type.STREAM, null, stream, length)
    }
}

/** Small completion handle whose listeners always run on the owner's callback dispatcher. */
internal class NearbyAction(private val dispatch: (() -> Unit) -> Unit) {
    private var complete = false
    private var failure: Exception? = null
    private val successes = mutableListOf<() -> Unit>()
    private val failures = mutableListOf<(Exception) -> Unit>()

    @Synchronized fun addOnSuccessListener(listener: () -> Unit): NearbyAction = apply {
        if (complete && failure == null) dispatch(listener) else if (!complete) successes += listener
    }
    @Synchronized fun addOnFailureListener(listener: (Exception) -> Unit): NearbyAction = apply {
        if (complete) failure?.let { error -> dispatch { listener(error) } } else failures += listener
    }
    @Synchronized fun succeed() = finish(null)
    @Synchronized fun fail(error: Exception) = finish(error)
    private fun finish(error: Exception?) {
        if (complete) return
        complete = true
        failure = error
        if (error == null) successes.forEach(dispatch) else failures.forEach { listener -> dispatch { listener(error) } }
        successes.clear()
        failures.clear()
    }
}
