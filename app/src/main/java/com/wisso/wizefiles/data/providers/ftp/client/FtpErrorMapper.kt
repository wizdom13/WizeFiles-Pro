// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp.client

import java.io.IOException
import java.net.SocketTimeoutException

internal object FtpErrorMapper {
    const val CONTROL_CONNECTION_LOST_REASON =
        "FTP control connection was lost. Reconnect and try again."

    fun isConnectionLost(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause }.filterIsInstance<IOException>().any { failure ->
            val message = failure.message?.lowercase().orEmpty()
            CONNECTION_LOST_MARKERS.any(message::contains)
        }

    fun connectionLostReason(throwable: IOException): String? {
        if (!isConnectionLost(throwable)) return null
        return throwable.message?.takeIf(String::isNotBlank)
            ?.let { "$CONTROL_CONNECTION_LOST_REASON ($it)" }
            ?: CONTROL_CONNECTION_LOST_REASON
    }

    fun dataConnectionReason(throwable: IOException): String? {
        val detail = throwable.message.orEmpty()
        val lower = detail.lowercase()
        if (generateSequence(throwable as Throwable?) { it.cause }.any { it is SocketTimeoutException }) {
            return "FTP data connection timed out. Check firewall/NAT and active/passive mode settings."
        }
        val failedToConnect = "failed to connect" in lower
        if (failedToConnect && ("econnrefused" in lower || "connection refused" in lower)) {
            return "FTP data connection was refused by the server/network. " +
                "Check firewall/NAT and active/passive mode settings. ($detail)"
        }
        if (failedToConnect && "after" in lower && "ms" in lower) {
            return "FTP data connection timed out. Check firewall/NAT and active/passive mode settings. ($detail)"
        }
        return null
    }

    private val CONNECTION_LOST_MARKERS = listOf(
        "broken pipe", "connection reset", "software caused connection abort",
        "connection abort", "connection closed", "connection is not open", "transfer aborted",
        "socket closed", "econnreset", "econnaborted"
    )
}
