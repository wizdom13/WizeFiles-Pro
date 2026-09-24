// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import android.os.Binder
import android.os.IInterface
import android.os.RemoteException
import java.io.IOException

@Throws(IOException::class)
fun <T : IInterface, R> T.invokeBridge(operation: T.(BridgeFailure) -> R): R {
    val failure = BridgeFailure()
    val result = try {
        operation(failure)
    } catch (remoteFailure: RemoteException) {
        throw BridgeUnavailableException(remoteFailure)
    }
    failure.value?.let { throw it }
    return result
}

fun <T, R> T.serveBridge(failure: BridgeFailure, operation: T.() -> R): R?
    where T : Binder, T : IInterface =
    try {
        operation()
    } catch (ioFailure: IOException) {
        failure.value = ioFailure
        null
    } catch (runtimeFailure: RuntimeException) {
        failure.value = runtimeFailure
        null
    }
