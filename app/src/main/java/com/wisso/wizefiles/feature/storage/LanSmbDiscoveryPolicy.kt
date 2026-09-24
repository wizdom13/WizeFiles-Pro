// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

internal object LanSmbDiscoveryPolicy {
    const val MAX_CONCURRENT_PROBES = 12

    /** Interleaves the /24 so responsive hosts across the range appear progressively. */
    fun hostOctets(): Sequence<Int> = sequence {
        for (offset in 0..99) {
            for (block in 0..2) {
                val octet = 100 * block + offset
                if (octet <= 255) yield(octet)
            }
        }
    }
}
