// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.os.CancellationSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// @see androidx.room.CoroutinesRoom.execute
suspend fun <T> runWithCancellationSignal(block: (CancellationSignal) -> T): T {
    val signal = CancellationSignal()
    return suspendCancellableCoroutine { continuation ->
        val job = CoroutineScope(Dispatchers.IO).launch {
            continuation.resume(block(signal))
        }
        continuation.invokeOnCancellation {
            signal.cancel()
            job.cancel()
        }
    }
}
