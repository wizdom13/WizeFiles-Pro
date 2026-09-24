// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.root

enum class RootStrategy {
    NEVER,
    AUTOMATIC,
    ALWAYS;

    internal fun selectsRoot(rootRequired: Boolean): Boolean = when (this) {
        NEVER -> false
        AUTOMATIC -> rootRequired
        ALWAYS -> true
    }
}
