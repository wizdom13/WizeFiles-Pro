// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import android.content.Context
import android.content.Intent
import java.nio.file.Path
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.vault.VaultActivity

@Parcelize
data class VaultStorage(
    val vaultId: String,
    val vaultName: String,
    override val isVisible: Boolean = true
) : Storage() {
    override val id: Long
        get() = ("VaultStorage:$vaultId").hashCode().toLong()

    override val iconRes: Int
        get() = R.drawable.ic_lock_white_24dp

    override val customName: String?
        get() = vaultName

    override fun getDefaultName(context: Context): String =
        context.getString(R.string.vault_default_name)

    override val description: String
        get() = vaultId

    @IgnoredOnParcel
    override val path: Path? = null

    override fun createIntent(): Intent = VaultActivity.createIntent(vaultId)

    override fun createEditIntent(): Intent = VaultActivity.createIntent(vaultId)
}
