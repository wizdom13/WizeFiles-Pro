// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Paths
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainPoliciesTest {
    @Test
    fun `JVM provider failures share bounded cause classification`() {
        val timeout = java.net.SocketTimeoutException("timed out")
        assertEquals(ProviderFailureSignal.TIMEOUT, ProviderJvmFailureClassifier.classify(timeout))
        assertEquals(
            ProviderFailureSignal.PERMISSION_REVOKED,
            ProviderJvmFailureClassifier.classify(java.nio.file.AccessDeniedException("source"))
        )
        val cyclic = java.io.IOException("outer")
        val inner = java.io.IOException("disk full")
        cyclic.initCause(inner)
        inner.initCause(cyclic)
        assertEquals(ProviderFailureSignal.DISK_FULL, ProviderJvmFailureClassifier.classify(cyclic))
    }
    @Test
    fun `operation reducer preserves monotonic recoverable progress`() {
        val running = OperationExecutionReducer.reduce(OperationExecutionSnapshot(), OperationExecutionEvent.Start)
        val progressed = OperationExecutionReducer.reduce(running, OperationExecutionEvent.Progress(12, 1))
        val failed = OperationExecutionReducer.reduce(
            progressed,
            OperationExecutionEvent.Fail(ProviderFailureMapper.map(ProviderFailureSignal.TIMEOUT, mutationStarted = true), true)
        )
        assertEquals(OperationExecutionState.RECOVERABLE, failed.state)
        assertEquals(ResumeCheckpoint(12, 1), failed.checkpoint)
        assertTrue(failed.destinationMayExist)
        assertThrows(IllegalArgumentException::class.java) {
            OperationExecutionReducer.reduce(progressed, OperationExecutionEvent.Progress(11, 1))
        }
    }

    @Test
    fun `permission failure waits for explicit user approval`() {
        val running = OperationExecutionReducer.reduce(OperationExecutionSnapshot(), OperationExecutionEvent.Start)
        val waiting = OperationExecutionReducer.reduce(
            running,
            OperationExecutionEvent.Fail(ProviderFailureMapper.map(ProviderFailureSignal.PERMISSION_REVOKED), false)
        )
        assertEquals(OperationExecutionState.WAITING_FOR_USER, waiting.state)
        val recoverable = OperationExecutionReducer.reduce(waiting, OperationExecutionEvent.UserApprovedRetry)
        assertEquals(OperationExecutionState.RECOVERABLE, recoverable.state)
    }

    @Test
    fun `provider failure mapping has bounded diagnostics and common retry policy`() {
        val unavailable = ProviderFailureMapper.map(ProviderFailureSignal.UNAVAILABLE, "x".repeat(2048))
        assertEquals(ProviderFailureMapper.MAX_MESSAGE_LENGTH, unavailable.message?.length)
        assertEquals(RetryClassification.TRANSIENT, unavailable.retryClassification)
        assertTrue(ProviderFailureMapper.map(ProviderFailureSignal.STALE_RESOURCE).requiresUserAction)
        assertEquals(
            RetryClassification.NEVER,
            ProviderFailureMapper.map(ProviderFailureSignal.MALFORMED_RESPONSE).retryClassification
        )
    }

    @Test
    fun `vault frame round trips and classifies hostile headers`() {
        val payload = byteArrayOf(0, 1, 2, -1)
        assertArrayEquals(payload, VaultPayloadFrame.decode(VaultPayloadFrame.encode(payload)))
        assertFrameFailure(byteArrayOf(), FrameFailureKind.EMPTY)
        assertFrameFailure(byteArrayOf(1, 2, 3), FrameFailureKind.TRUNCATED)
        assertFrameFailure(ByteArray(9), FrameFailureKind.BAD_MAGIC)
        val unsupported = VaultPayloadFrame.encode(payload).also { it[4] = 99 }
        assertFrameFailure(unsupported, FrameFailureKind.UNSUPPORTED_VERSION)
        val negative = VaultPayloadFrame.encode(payload).also {
            ByteBuffer.wrap(it, 5, 4).order(ByteOrder.BIG_ENDIAN).putInt(-1)
        }
        assertFrameFailure(negative, FrameFailureKind.INVALID_LENGTH)
        val oversized = VaultPayloadFrame.encode(payload).also {
            ByteBuffer.wrap(it, 5, 4).order(ByteOrder.BIG_ENDIAN)
                .putInt(VaultPayloadFrame.MAX_PAYLOAD_BYTES + 1)
        }
        assertFrameFailure(oversized, FrameFailureKind.OVERSIZED)
    }

    @Test
    fun `deterministic vault frame fuzz never returns altered data or unexpected exceptions`() {
        val random = Random(0x575A46)
        repeat(10_000) {
            val bytes = random.nextBytes(random.nextInt(0, 512))
            try {
                val decoded = VaultPayloadFrame.decode(bytes)
                assertArrayEquals(bytes, VaultPayloadFrame.encode(decoded))
            } catch (expected: FrameValidationException) {
                // Every rejected seed is reproducible from the fixed RNG seed above.
            }
        }
    }

    @Test
    fun `vault relock state requires deadline after backgrounding`() {
        var state = VaultLockReducer.reduce(VaultLockState.LOCKED, VaultLockEvent.UnlockSucceeded)
        state = VaultLockReducer.reduce(state, VaultLockEvent.AppBackgrounded)
        assertEquals(VaultLockState.RELOCK_REQUIRED, state)
        state = VaultLockReducer.reduce(state, VaultLockEvent.RelockDeadlineReached)
        assertEquals(VaultLockState.LOCKED, state)
    }

    @Test
    fun `nearby authentication state rejects invalid peer ordering and recovers connection loss`() {
        var state = NearbySessionReducer.reduce(NearbySessionState.IDLE, NearbySessionEvent.BeginDiscovery)
        state = NearbySessionReducer.reduce(state, NearbySessionEvent.PeerFound)
        state = NearbySessionReducer.reduce(state, NearbySessionEvent.AuthenticationAccepted)
        state = NearbySessionReducer.reduce(state, NearbySessionEvent.TransferStarted)
        assertEquals(
            NearbySessionState.RECOVERABLE,
            NearbySessionReducer.reduce(state, NearbySessionEvent.ConnectionFailed(transient = true))
        )
        assertThrows(IllegalArgumentException::class.java) {
            NearbySessionReducer.reduce(NearbySessionState.IDLE, NearbySessionEvent.AuthenticationAccepted)
        }
    }

    @Test
    fun `nearby authentication failure is terminal and paused transfers resume explicitly`() {
        var state = NearbySessionReducer.reduce(NearbySessionState.IDLE, NearbySessionEvent.BeginDiscovery)
        state = NearbySessionReducer.reduce(state, NearbySessionEvent.PeerFound)
        assertEquals(
            NearbySessionState.FAILED,
            NearbySessionReducer.reduce(state, NearbySessionEvent.ConnectionFailed(transient = true))
        )
        state = NearbySessionReducer.reduce(NearbySessionState.TRANSFERRING, NearbySessionEvent.Pause)
        assertEquals(NearbySessionState.PAUSED, state)
        assertEquals(
            NearbySessionState.TRANSFERRING,
            NearbySessionReducer.reduce(state, NearbySessionEvent.ResumeTransfer)
        )
    }

    @Test
    fun `nearby resume cannot bypass peer authentication`() {
        assertThrows(IllegalArgumentException::class.java) {
            NearbySessionReducer.reduce(NearbySessionState.IDLE, NearbySessionEvent.ResumeTransfer)
        }
        val authenticating = NearbySessionReducer.reduce(
            NearbySessionState.DISCOVERING,
            NearbySessionEvent.PeerFound
        )
        val failed = NearbySessionReducer.reduce(
            authenticating,
            NearbySessionEvent.ConnectionFailed(transient = true)
        )
        assertEquals(NearbySessionState.FAILED, failed)
        assertThrows(IllegalArgumentException::class.java) {
            NearbySessionReducer.reduce(failed, NearbySessionEvent.ResumeTransfer)
        }
    }

    @Test
    fun `nearby terminal sessions reject late connection failures`() {
        listOf(
            NearbySessionState.COMPLETED,
            NearbySessionState.CANCELLED,
            NearbySessionState.FAILED
        ).forEach { state ->
            assertThrows(state.name, IllegalArgumentException::class.java) {
                NearbySessionReducer.reduce(
                    state,
                    NearbySessionEvent.ConnectionFailed(transient = true)
                )
            }
            assertThrows(state.name, IllegalArgumentException::class.java) {
                NearbySessionReducer.reduce(
                    state,
                    NearbySessionEvent.ConnectionFailed(transient = false)
                )
            }
        }
    }

    @Test
    fun `secure relative paths reject archive and nearby traversal corpus`() {
        val hostile = listOf(
            "../escape", "a/../escape", "/absolute", "\\absolute", "C:/drive",
            "a\\b", "a//b", "./entry", "a/./b", "a/\u0000b",
            List(SecureRelativePath.MAX_DEPTH + 1) { "x" }.joinToString("/")
        )
        hostile.forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) { SecureRelativePath.validate(value) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecureRelativePath.validate("😀".repeat(64))
        }
        val root = Paths.get("build", "staging")
        val resolved = SecureRelativePath.resolveInside(root, "safe/entry.txt")
        assertTrue(resolved.startsWith(root.toAbsolutePath().normalize()))
        assertFalse(resolved == root.toAbsolutePath().normalize())
    }

    private fun assertFrameFailure(frame: ByteArray, kind: FrameFailureKind) {
        val failure = assertThrows(FrameValidationException::class.java) { VaultPayloadFrame.decode(frame) }
        assertEquals(kind, failure.kind)
    }
}
