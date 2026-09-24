// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.util.concurrent.Executor

/** Rechecks freshness on the delivery executor, not only when work completes. */
internal class LatestGenerationPublisher<T>(
    private val generation: SearchGeneration,
    private val deliveryExecutor: Executor,
    private val deliver: (T) -> Unit
) {
    fun publish(candidate: Long, value: T) {
        deliveryExecutor.execute {
            generation.runIfCurrent(candidate) { deliver(value) }
        }
    }
}
