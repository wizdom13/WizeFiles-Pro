// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

internal class SearchGeneration {
    private var value = 0L

    @Synchronized
    fun next(): Long = ++value

    @Synchronized
    fun current(): Long = value

    @Synchronized
    fun isCurrent(candidate: Long): Boolean = value == candidate

    @Synchronized
    fun runIfCurrent(candidate: Long, action: () -> Unit): Boolean {
        if (value != candidate) return false
        action()
        return true
    }
}
