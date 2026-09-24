// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

internal class TransferScheduler(
    private val maximumConcurrentTransfers: Int = TransferExecutionPolicy.MAXIMUM_CONCURRENT_TRANSFERS
) {
    init {
        require(maximumConcurrentTransfers > 0)
    }

    fun selectRunnable(
        queued: List<TransferOperationRecord>,
        running: List<TransferOperationRecord>
    ): List<TransferOperationRecord> {
        val freeSlots = (maximumConcurrentTransfers - running.size).coerceAtLeast(0)
        if (freeSlots == 0) return emptyList()
        val lockedDestinations = running.map { normalizeScope(it.destinationUri) }.toMutableList()
        return buildList {
            for (operation in queued.sortedBy(TransferOperationRecord::queuePosition)) {
                if (size >= freeSlots) break
                val destination = normalizeScope(operation.destinationUri)
                if (lockedDestinations.none { scopesOverlap(it, destination) }) {
                    add(operation)
                    lockedDestinations += destination
                }
            }
        }
    }

    private fun normalizeScope(uri: String): String = uri.trimEnd('/')

    private fun scopesOverlap(first: String, second: String): Boolean =
        first == second || first.startsWith("$second/") || second.startsWith("$first/")
}
