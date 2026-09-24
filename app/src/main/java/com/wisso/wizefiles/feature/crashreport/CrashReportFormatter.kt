// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.crashreport

data class CrashReportMetadata(
    val appVersion: String,
    val versionCode: Long,
    val buildType: String,
    val timestamp: String,
    val androidVersion: String,
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val processName: String,
    val collectorThread: String
)

object CrashReportFormatter {
    fun format(metadata: CrashReportMetadata, stackTrace: String): String = buildString {
        appendLine("WizeFiles crash report")
        appendLine("======================")
        appendLine("App: ${metadata.appVersion} (${metadata.versionCode})")
        appendLine("Build type: ${metadata.buildType}")
        appendLine("Time (UTC): ${metadata.timestamp}")
        appendLine("Android: ${metadata.androidVersion} (API ${metadata.apiLevel})")
        appendLine("Device: ${metadata.manufacturer} ${metadata.model}")
        appendLine("Process: ${metadata.processName}")
        appendLine("Collector thread: ${metadata.collectorThread}")
        appendLine()
        appendLine("Stack trace")
        appendLine("-----------")
        append(stackTrace.ifBlank { "<stack trace unavailable>" })
    }

    fun appendUserDetails(baseReport: String, comment: String, diagnosticLog: String): String =
        buildString {
            append(baseReport.trimEnd())
            if (comment.isNotBlank()) {
                appendLine()
                appendLine()
                appendLine("User comment")
                appendLine("------------")
                append(comment.trim())
            }
            if (diagnosticLog.isNotBlank()) {
                appendLine()
                appendLine()
                appendLine("Recent diagnostic log (user opted in)")
                appendLine("--------------------------------------")
                append(diagnosticLog.trim())
            }
            appendLine()
        }
}
