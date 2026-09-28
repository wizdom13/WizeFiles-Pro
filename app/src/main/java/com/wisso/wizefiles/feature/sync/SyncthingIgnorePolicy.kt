// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

internal object SyncthingIgnorePolicy {
    private const val BEGIN = "// WizeFiles managed rules begin"
    private const val END = "// WizeFiles managed rules end"

    fun merge(existing: List<String>, managed: List<String>): List<String> {
        val user = mutableListOf<String>()
        var inside = false
        existing.forEach { line ->
            when (line) {
                BEGIN -> { require(!inside) { "Invalid managed ignore block" }; inside = true }
                END -> { require(inside) { "Invalid managed ignore block" }; inside = false }
                else -> if (!inside) user.add(line)
            }
        }
        require(!inside) { "Incomplete managed ignore block" }
        // Preserve user rules verbatim; Syncthing uses first-match precedence.
        return user + listOf(BEGIN) + managed + listOf(END)
    }
}
