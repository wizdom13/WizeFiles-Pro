// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.advancedformats.sandbox

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Permissionless parser boundary for untrusted binary formats. The isolated process receives only
 * caller-created descriptors and bounded primitive values; it cannot resolve WizeFiles paths.
 */
class FormatSandboxService : Service() {
    private val nextRequestId = AtomicLong(1)
    private val executor = Executors.newFixedThreadPool(MAX_PARALLEL_OPERATIONS)
    private val operations = ConcurrentHashMap<Long, Operation>()

    private val binder = object : IFormatSandboxService.Stub() {
        override fun submit(
            request: FormatSandboxRequest,
            input: ParcelFileDescriptor,
            output: ParcelFileDescriptor,
            callback: IFormatSandboxCallback
        ): Long {
            val requestId = nextRequestId.getAndIncrement()
            request.validationError()?.let { message ->
                closeQuietly(input)
                closeQuietly(output)
                runCatching {
                    callback.onFailed(requestId, FormatSandboxError.INVALID_REQUEST, message)
                }
                return requestId
            }

            lateinit var operation: Operation
            val task = FutureTask<Unit> {
                execute(operation)
            }
            operation = Operation(requestId, request, input, output, callback, task)
            operations[requestId] = operation
            executor.execute(task)
            return requestId
        }

        override fun cancel(requestId: Long) {
            operations.remove(requestId)?.cancel()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        operations.values.toList().forEach(Operation::cancel)
        operations.clear()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun execute(operation: Operation) {
        var inputBytes = 0L
        var outputBytes = 0L
        val deadline = SystemClock.elapsedRealtime() + operation.request.timeoutMillis
        try {
            ParcelFileDescriptor.AutoCloseInputStream(operation.input).use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(operation.output).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (
                        inputBytes < operation.request.maxInputBytes &&
                        outputBytes < operation.request.maxOutputBytes
                    ) {
                        operation.throwIfStopped(deadline)
                        val remaining = minOf(
                            operation.request.maxInputBytes - inputBytes,
                            operation.request.maxOutputBytes - outputBytes,
                            buffer.size.toLong()
                        ).toInt()
                        val count = input.read(buffer, 0, remaining)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        inputBytes += count
                        outputBytes += count
                    }
                    output.flush()
                }
            }
            operation.complete(inputBytes, outputBytes)
        } catch (exception: Exception) {
            operation.fail(exception)
        } finally {
            operations.remove(operation.requestId, operation)
            operation.closeDescriptors()
        }
    }

    private class Operation(
        val requestId: Long,
        val request: FormatSandboxRequest,
        val input: ParcelFileDescriptor,
        val output: ParcelFileDescriptor,
        private val callback: IFormatSandboxCallback,
        private val task: FutureTask<Unit>
    ) {
        private val cancelled = AtomicBoolean(false)
        private val finished = AtomicBoolean(false)

        fun throwIfStopped(deadline: Long) {
            if (cancelled.get() || Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("Sandbox operation cancelled")
            }
            if (SystemClock.elapsedRealtime() > deadline) {
                throw SandboxTimeoutException()
            }
        }

        fun complete(inputBytes: Long, outputBytes: Long) {
            if (!finished.compareAndSet(false, true)) return
            runCatching {
                callback.onCompleted(FormatSandboxResult(requestId, inputBytes, outputBytes))
            }
        }

        fun fail(exception: Exception) {
            if (cancelled.get()) {
                finishCancelled()
                return
            }
            if (!finished.compareAndSet(false, true)) return
            val code = when (exception) {
                is SandboxTimeoutException -> FormatSandboxError.TIMED_OUT
                is IOException -> FormatSandboxError.IO
                else -> FormatSandboxError.INTERNAL
            }
            val message = (exception.message ?: exception.javaClass.simpleName)
                .replace('\n', ' ')
                .take(MAX_ERROR_MESSAGE_LENGTH)
            runCatching { callback.onFailed(requestId, code, message) }
        }

        fun cancel() {
            if (!cancelled.compareAndSet(false, true)) return
            finishCancelled()
            closeDescriptors()
            task.cancel(true)
        }

        private fun finishCancelled() {
            if (!finished.compareAndSet(false, true)) return
            runCatching { callback.onCancelled(requestId) }
        }

        fun closeDescriptors() {
            closeQuietly(input)
            closeQuietly(output)
        }
    }

    private class SandboxTimeoutException : InterruptedIOException("Sandbox operation timed out")

    companion object {
        private const val MAX_PARALLEL_OPERATIONS = 2
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_ERROR_MESSAGE_LENGTH = 160

        private fun closeQuietly(descriptor: ParcelFileDescriptor) {
            try {
                descriptor.close()
            } catch (_: IOException) {
                // The stream or cancellation path may already own and close the descriptor.
            }
        }
    }
}
