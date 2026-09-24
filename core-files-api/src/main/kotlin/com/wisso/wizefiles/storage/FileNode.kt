// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

/** A provider-neutral file identity; implementations must not expose credentials in these values. */
interface FileNode {
    /** Identifier for the backend that owns this node (for example, `local`). */
    val backendId: String

    /** Stable display path for diagnostics and UI breadcrumbs. */
    val path: String

    /** Last path segment for user-facing naming. */
    val name: String
}
