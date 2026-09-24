// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import android.os.Parcelable
import com.wisso.wizefiles.provider.common.UriAuthority
import kotlinx.parcelize.Parcelize
import net.schmizz.sshj.SSHClient

@Parcelize
data class Authority(
    val host: String,
    val port: Int,
    val username: String
) : Parcelable {
    fun toUriAuthority(): UriAuthority =
        UriAuthority(
            userInfo = username.ifBlank { null },
            host = host,
            port = port.takeUnless { candidate -> candidate == DEFAULT_PORT }
        )

    override fun toString(): String = toUriAuthority().toString()

    companion object {
        const val DEFAULT_PORT: Int = SSHClient.DEFAULT_PORT
    }
}
