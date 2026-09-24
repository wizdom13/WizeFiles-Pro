// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.packageinstaller

internal object PrivilegedInstallCommands {
    private val sessionPattern = Regex("(?:session\\s*)?\\[(\\d+)]", RegexOption.IGNORE_CASE)

    fun create(totalBytes: Long, allowDowngrade: Boolean, targetUserId: Int?): String {
        require(totalBytes > 0)
        require(targetUserId == null || targetUserId >= 0)
        return buildList {
            add("pm install-create")
            add("-r")
            add("-S $totalBytes")
            if (allowDowngrade) add("-d")
            targetUserId?.let { add("--user $it") }
        }.joinToString(" ")
    }

    fun write(sessionId: Int, splitName: String, sizeBytes: Long, path: String): String {
        require(sessionId >= 0)
        require(sizeBytes > 0)
        return "pm install-write -S $sizeBytes $sessionId ${shellQuote(splitName)} " +
            shellQuote(path)
    }

    fun commit(sessionId: Int): String {
        require(sessionId >= 0)
        return "pm install-commit $sessionId"
    }

    fun abandon(sessionId: Int): String {
        require(sessionId >= 0)
        return "pm install-abandon $sessionId"
    }

    fun parseSessionId(output: String): Int? =
        sessionPattern.find(output)?.groupValues?.get(1)?.toIntOrNull()

}

internal fun shellQuote(value: String): String {
    require('\u0000' !in value && '\n' !in value && '\r' !in value)
    return "'${value.replace("'", "'\\''")}'"
}
