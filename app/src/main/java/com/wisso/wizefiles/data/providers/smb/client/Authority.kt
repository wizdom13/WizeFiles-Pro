// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb.client

import android.os.Parcelable
import com.hierynomus.smbj.SMBClient
import com.wisso.wizefiles.provider.common.UriAuthority
import kotlinx.parcelize.Parcelize

@Parcelize
data class Authority(
    val host: String,
    val port: Int,
    val username: String,
    val domain: String?
) : Parcelable {
    fun toUriAuthority(): UriAuthority {
        val account = username.takeIf { it.isNotBlank() }
        val qualifiedAccount = when {
            account == null -> null
            domain.isNullOrBlank() -> account
            else -> domain + DOMAIN_SEPARATOR + account
        }
        return UriAuthority(
            userInfo = qualifiedAccount,
            host = host,
            port = port.takeUnless { candidate -> candidate == DEFAULT_PORT }
        )
    }

    override fun toString(): String = toUriAuthority().toString()

    companion object {
        const val DEFAULT_PORT: Int = SMBClient.DEFAULT_PORT
        private const val DOMAIN_SEPARATOR = "\\"
    }
}
