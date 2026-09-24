// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream

@Throws(IOException::class)
fun InputStream.copyTo(
    outputStream: OutputStream,
    intervalMillis: Long,
    listener: ((Long) -> Unit)?
) {
    require(intervalMillis >= 0) { "Progress interval must not be negative" }
    val transferBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val intervalNanos = intervalMillis
        .coerceAtMost(Long.MAX_VALUE / NANOS_PER_MILLISECOND) * NANOS_PER_MILLISECOND
    var unreportedBytes = 0L
    var lastReportNanos = System.nanoTime()

    while (true) {
        val count = read(transferBuffer)
        if (count < 0) break
        if (count == 0) continue

        outputStream.write(transferBuffer, 0, count)
        unreportedBytes += count
        ensureThreadIsRunning()

        val now = System.nanoTime()
        if (listener != null && now - lastReportNanos >= intervalNanos) {
            listener(unreportedBytes)
            unreportedBytes = 0
            lastReportNanos = now
        }
    }
    listener?.invoke(unreportedBytes)
}

@Throws(IOException::class)
fun InputStream.readFully(buffer: ByteArray, offset: Int, length: Int): Int {
    require(offset >= 0 && length >= 0 && offset <= buffer.size - length) {
        "Invalid destination range: offset=$offset, length=$length, size=${buffer.size}"
    }
    var cursor = offset
    val end = offset + length
    while (cursor < end) {
        val count = read(buffer, cursor, end - cursor)
        if (count < 0) break
        if (count == 0) continue
        cursor += count
    }
    return cursor - offset
}

@Throws(InterruptedIOException::class)
private fun ensureThreadIsRunning() {
    if (Thread.currentThread().isInterrupted) {
        throw InterruptedIOException("Stream copy interrupted")
    }
}

private const val NANOS_PER_MILLISECOND = 1_000_000L
