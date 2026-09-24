// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.Intent
import com.wisso.wizefiles.util.createIntent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wisso.wizefiles.R

class VaultInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vaultId = intent.getStringExtra(EXTRA_VAULT_ID) ?: run { finish(); return }
        val metadata = VaultManager(this).listVaults().firstOrNull { it.vaultId == vaultId }
        if (metadata == null) {
            finish()
            return
        }
        title = metadata.name
        startActivity(VaultActivity.createIntent(vaultId))
        finish()
    }

    companion object {
        private const val EXTRA_VAULT_ID = "vault_id"

        fun createIntent(vaultId: String): Intent = VaultInfoActivity::class.createIntent()
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_VAULT_ID, vaultId)
    }
}
