// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class PauseRequestedException : InterruptedIOException("Transfer pause requested")
internal class CancelRequestedException : InterruptedIOException("Transfer cancel requested")

internal class OperationControl {
    private val pauseRequested = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)

    fun requestPause() {
        pauseRequested.set(true)
    }

    fun throwIfPauseRequested() {
        if (cancelRequested.get()) throw CancelRequestedException()
        if (pauseRequested.get()) throw PauseRequestedException()
    }

    fun requestCancel() {
        cancelRequested.set(true)
    }
}

internal object OperationControlRegistry {
    private val controls = ConcurrentHashMap<String, OperationControl>()

    fun attach(operationId: String): OperationControl =
        OperationControl().also { controls[operationId] = it }

    fun requestPause(operationId: String): Boolean {
        val control = controls[operationId] ?: return false
        control.requestPause()
        return true
    }

    fun requestCancel(operationId: String): Boolean {
        val control = controls[operationId] ?: return false
        control.requestCancel()
        return true
    }

    fun throwIfPauseRequested(operationId: String?) {
        operationId?.let { controls[it] }?.throwIfPauseRequested()
    }

    fun detach(operationId: String?) {
        operationId?.let(controls::remove)
    }

    fun isAttached(operationId: String): Boolean = controls.containsKey(operationId)
}
