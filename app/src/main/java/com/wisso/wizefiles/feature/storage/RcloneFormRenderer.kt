// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

internal data class RcloneFormVisibility(
    val webDav: Boolean,
    val s3: Boolean,
    val imported: Boolean,
    val oauth: Boolean,
    val mega: Boolean,
    val advanced: Boolean,
    val dynamic: Boolean
)

internal object RcloneFormRenderer {
    fun visibility(provider: ProviderChoice, powerUser: Boolean, editing: Boolean) =
        RcloneFormVisibility(
            webDav = !editing && !powerUser && provider.backendType == "webdav",
            s3 = !editing && !powerUser && provider.backendType == "s3",
            imported = !editing && provider.isImport,
            oauth = !editing && !powerUser &&
                regularSetupFor(provider.backendType) == RcloneRegularSetup.OAUTH,
            mega = !editing && !powerUser &&
                regularSetupFor(provider.backendType) == RcloneRegularSetup.MEGA_CREDENTIALS,
            advanced = !editing && powerUser && !provider.isImport && provider.definition == null,
            dynamic = !editing && !provider.isImport && provider.definition != null &&
                (powerUser || regularSetupFor(provider.backendType) == RcloneRegularSetup.GENERATED)
        )
}
