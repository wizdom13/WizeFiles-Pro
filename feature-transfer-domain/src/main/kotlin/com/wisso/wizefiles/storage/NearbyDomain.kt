// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.nio.charset.StandardCharsets
import java.nio.file.Path

enum class NearbySessionState { IDLE, DISCOVERING, AUTHENTICATING, CONNECTED, TRANSFERRING, PAUSED, RECOVERABLE, COMPLETED, CANCELLED, FAILED }
sealed interface NearbySessionEvent {
    data object BeginDiscovery : NearbySessionEvent
    data object ResumeTransfer : NearbySessionEvent
    data object PeerFound : NearbySessionEvent
    data object AuthenticationAccepted : NearbySessionEvent
    data object TransferStarted : NearbySessionEvent
    data object Pause : NearbySessionEvent
    data object TransferCompleted : NearbySessionEvent
    data class ConnectionFailed(val transient: Boolean) : NearbySessionEvent
    data object Cancel : NearbySessionEvent
}

object NearbySessionReducer {
    fun reduce(state: NearbySessionState, event: NearbySessionEvent): NearbySessionState = when (event) {
        NearbySessionEvent.BeginDiscovery -> requireState(state, NearbySessionState.IDLE, NearbySessionState.DISCOVERING)
        NearbySessionEvent.ResumeTransfer -> {
            require(state == NearbySessionState.PAUSED || state == NearbySessionState.RECOVERABLE) {
                "Only an authenticated paused or recoverable session can resume"
            }
            NearbySessionState.TRANSFERRING
        }
        NearbySessionEvent.PeerFound -> requireState(state, NearbySessionState.DISCOVERING, NearbySessionState.AUTHENTICATING)
        NearbySessionEvent.AuthenticationAccepted -> requireState(state, NearbySessionState.AUTHENTICATING, NearbySessionState.CONNECTED)
        NearbySessionEvent.TransferStarted -> requireState(state, NearbySessionState.CONNECTED, NearbySessionState.TRANSFERRING)
        NearbySessionEvent.Pause -> requireState(state, NearbySessionState.TRANSFERRING, NearbySessionState.PAUSED)
        NearbySessionEvent.TransferCompleted -> requireState(state, NearbySessionState.TRANSFERRING, NearbySessionState.COMPLETED)
        is NearbySessionEvent.ConnectionFailed -> when {
            state in TERMINAL_STATES ->
                throw IllegalArgumentException("A terminal nearby session cannot fail again")
            !event.transient -> NearbySessionState.FAILED
            state == NearbySessionState.DISCOVERING -> NearbySessionState.FAILED
            state == NearbySessionState.AUTHENTICATING -> NearbySessionState.FAILED
            state in RECOVERABLE_STATES -> NearbySessionState.RECOVERABLE
            else -> NearbySessionState.FAILED
        }
        NearbySessionEvent.Cancel -> {
            require(state !in TERMINAL_STATES) { "A terminal nearby session cannot be cancelled" }
            NearbySessionState.CANCELLED
        }
    }

    private fun requireState(actual: NearbySessionState, expected: NearbySessionState, next: NearbySessionState): NearbySessionState {
        require(actual == expected) { "Expected $expected but was $actual" }
        return next
    }

    private val RECOVERABLE_STATES = setOf(
        NearbySessionState.CONNECTED,
        NearbySessionState.TRANSFERRING,
        NearbySessionState.PAUSED
    )
    private val TERMINAL_STATES = setOf(NearbySessionState.COMPLETED, NearbySessionState.CANCELLED, NearbySessionState.FAILED)
}

/** Path policy shared by archive, Nearby, and remote-provider staging adapters. */
object SecureRelativePath {
    const val MAX_UTF8_BYTES = 4096
    const val MAX_DEPTH = 128
    const val MAX_SEGMENT_LENGTH = 255

    fun validate(value: String): String {
        require(value.isNotBlank()) { "Relative path is empty" }
        require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_UTF8_BYTES) { "Relative path is too long" }
        require(!value.startsWith('/') && !value.startsWith('\\')) { "Absolute path is forbidden" }
        require(!(value.length >= 2 && value[0].isAsciiLetter() && value[1] == ':')) {
            "Drive path is forbidden"
        }
        require('\\' !in value) { "Backslash is forbidden" }
        require(value.none { it.code < 32 || it.code == 127 }) { "Control character is forbidden" }
        val segments = value.split('/')
        require(segments.size <= MAX_DEPTH) { "Relative path is too deep" }
        require(segments.all {
            it.isNotEmpty() && it != "." && it != ".." &&
                it.toByteArray(StandardCharsets.UTF_8).size <= MAX_SEGMENT_LENGTH
        }) {
            "Unsafe path segment"
        }
        return segments.joinToString("/")
    }

    fun resolveInside(root: Path, relative: String): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val target = normalizedRoot.resolve(validate(relative)).normalize()
        require(target.startsWith(normalizedRoot) && target != normalizedRoot) { "Path escapes root" }
        return target
    }
}

private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

enum class NearbyPayloadDirection { INCOMING, OUTGOING }
enum class NearbyPayloadState { PENDING, READY, ACTIVE }

data class NearbyPayloadCheckpoint(
    val payloadId: Long,
    val direction: NearbyPayloadDirection,
    val state: NearbyPayloadState
)

enum class NearbyDurableOperationState { ACTIVE, TERMINAL, CANCELLED, MISSING }

data class NearbyPayloadRecovery(
    val ledger: NearbyPayloadLedger,
    val discardedCheckpointCount: Int,
    val stale: Boolean,
    val durableState: NearbyDurableOperationState
)

/**
 * Reconciles a persisted correlation snapshot after process recreation. Nearby stream handles are
 * process-local, so no persisted payload (including one formerly marked ACTIVE) is admitted into
 * the new ledger. The durable transfer repository remains authoritative and a reconnect must
 * establish fresh payload IDs and transport handles before work can be claimed again.
 */
object NearbyPayloadRecoveryPolicy {
    const val MAX_CHECKPOINTS = 64
    const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

    fun restore(
        checkpoints: List<NearbyPayloadCheckpoint>,
        durableState: NearbyDurableOperationState,
        updatedAtMillis: Long,
        nowMillis: Long,
        maxPendingIncoming: Int
    ): NearbyPayloadRecovery {
        val stale = updatedAtMillis <= 0 || nowMillis < updatedAtMillis ||
            nowMillis - updatedAtMillis > MAX_AGE_MILLIS
        val boundedCount = checkpoints.take(MAX_CHECKPOINTS).size
        return NearbyPayloadRecovery(
            ledger = NearbyPayloadLedger(maxPendingIncoming),
            discardedCheckpointCount = boundedCount,
            stale = stale,
            durableState = durableState
        )
    }
}

/**
 * Correlates payload and metadata callbacks without retaining transport handles. Its checkpoints
 * are safe for an Android persistence adapter to serialize during process-death recovery.
 */
class NearbyPayloadLedger(private val maxPendingIncoming: Int) {
    private val incomingPayloads = mutableSetOf<Long>()
    private val incomingMetadata = mutableSetOf<Long>()
    private val outgoing = mutableSetOf<Long>()
    var activePayloadId: Long? = null
        private set

    init { require(maxPendingIncoming > 0) }

    fun acceptIncomingPayload(payloadId: Long): Boolean {
        if (payloadId == activePayloadId || payloadId in incomingPayloads ||
            (payloadId !in incomingMetadata && pendingIncomingIds().size >= maxPendingIncoming)
        ) return false
        incomingPayloads += payloadId
        return true
    }

    fun acceptIncomingMetadata(payloadId: Long): Boolean {
        if (payloadId == activePayloadId || payloadId in incomingMetadata ||
            (payloadId !in incomingPayloads && pendingIncomingIds().size >= maxPendingIncoming)
        ) return false
        incomingMetadata += payloadId
        return true
    }

    fun claimIncoming(payloadId: Long): Boolean {
        if (activePayloadId != null || payloadId !in incomingPayloads || payloadId !in incomingMetadata) return false
        incomingPayloads -= payloadId
        incomingMetadata -= payloadId
        activePayloadId = payloadId
        return true
    }

    fun trackOutgoing(payloadId: Long): Boolean {
        if (activePayloadId != null || payloadId in outgoing || payloadId in pendingIncomingIds()) return false
        outgoing += payloadId
        activePayloadId = payloadId
        return true
    }

    fun finish(payloadId: Long): Boolean {
        val known = outgoing.remove(payloadId) || activePayloadId == payloadId
        if (activePayloadId == payloadId) activePayloadId = null
        return known
    }

    fun completeIncoming(): Long? = activePayloadId.also { activePayloadId = null }

    fun checkpoints(): List<NearbyPayloadCheckpoint> = buildList {
        pendingIncomingIds().sorted().forEach { id ->
            add(NearbyPayloadCheckpoint(
                id,
                NearbyPayloadDirection.INCOMING,
                if (id in incomingPayloads && id in incomingMetadata) NearbyPayloadState.READY else NearbyPayloadState.PENDING
            ))
        }
        outgoing.sorted().forEach { id ->
            add(NearbyPayloadCheckpoint(id, NearbyPayloadDirection.OUTGOING,
                if (id == activePayloadId) NearbyPayloadState.ACTIVE else NearbyPayloadState.PENDING))
        }
        activePayloadId?.takeIf { it !in outgoing }?.let { id ->
            add(NearbyPayloadCheckpoint(id, NearbyPayloadDirection.INCOMING, NearbyPayloadState.ACTIVE))
        }
    }

    fun clear() {
        incomingPayloads.clear()
        incomingMetadata.clear()
        outgoing.clear()
        activePayloadId = null
    }

    private fun pendingIncomingIds(): Set<Long> = incomingPayloads + incomingMetadata
}
