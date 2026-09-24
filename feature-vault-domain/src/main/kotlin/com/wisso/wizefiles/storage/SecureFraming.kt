// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class FrameFailureKind { EMPTY, BAD_MAGIC, UNSUPPORTED_VERSION, TRUNCATED, INVALID_LENGTH, OVERSIZED }

class FrameValidationException(val kind: FrameFailureKind, message: String) : IllegalArgumentException(message)

/** Versioned, length-delimited framing. Encryption/authentication remains in the app crypto adapter. */
object VaultPayloadFrame {
    private val MAGIC = byteArrayOf('W'.code.toByte(), 'Z'.code.toByte(), 'V'.code.toByte(), 'F'.code.toByte())
    private const val VERSION: Byte = 1
    private const val HEADER_SIZE = 9
    const val MAX_PAYLOAD_BYTES = 64 * 1024 * 1024

    fun encode(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Vault payload is too large" }
        return ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC).put(VERSION).putInt(payload.size).put(payload).array()
    }

    fun decode(frame: ByteArray): ByteArray {
        if (frame.isEmpty()) fail(FrameFailureKind.EMPTY, "Vault frame is empty")
        if (frame.size < HEADER_SIZE) fail(FrameFailureKind.TRUNCATED, "Vault frame header is truncated")
        if (!frame.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            fail(FrameFailureKind.BAD_MAGIC, "Vault frame magic is invalid")
        }
        if (frame[4] != VERSION) fail(FrameFailureKind.UNSUPPORTED_VERSION, "Vault frame version is unsupported")
        val length = ByteBuffer.wrap(frame, 5, Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).int
        if (length < 0) fail(FrameFailureKind.INVALID_LENGTH, "Vault frame length is negative")
        if (length > MAX_PAYLOAD_BYTES) fail(FrameFailureKind.OVERSIZED, "Vault frame is too large")
        if (frame.size != HEADER_SIZE + length) fail(FrameFailureKind.TRUNCATED, "Vault frame length does not match data")
        return frame.copyOfRange(HEADER_SIZE, frame.size)
    }

    private fun fail(kind: FrameFailureKind, message: String): Nothing = throw FrameValidationException(kind, message)
}

enum class VaultLockState { LOCKED, UNLOCKED, RELOCK_REQUIRED }
sealed interface VaultLockEvent {
    data object UnlockSucceeded : VaultLockEvent
    data object ExplicitLock : VaultLockEvent
    data object AppBackgrounded : VaultLockEvent
    data object RelockDeadlineReached : VaultLockEvent
    data object KeyInvalidated : VaultLockEvent
}

object VaultLockReducer {
    fun reduce(state: VaultLockState, event: VaultLockEvent): VaultLockState = when (event) {
        VaultLockEvent.UnlockSucceeded -> VaultLockState.UNLOCKED
        VaultLockEvent.ExplicitLock,
        VaultLockEvent.KeyInvalidated -> VaultLockState.LOCKED
        VaultLockEvent.AppBackgrounded -> if (state == VaultLockState.UNLOCKED) {
            VaultLockState.RELOCK_REQUIRED
        } else state
        VaultLockEvent.RelockDeadlineReached -> if (state == VaultLockState.RELOCK_REQUIRED) {
            VaultLockState.LOCKED
        } else state
    }
}
