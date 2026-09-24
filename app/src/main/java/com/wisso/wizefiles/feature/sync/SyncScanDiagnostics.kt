// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

internal data class SyncScanDiagnostic(
    val side: SyncSide?,
    val endpointUri: String,
    val detail: String
)

internal fun encodeSyncScanDiagnostics(
    source: SyncScanResult,
    destination: SyncScanResult,
    endpointError: String?
): String = buildList {
    source.errors.forEach { error ->
        add(syncScanDiagnosticLine(SyncSide.SOURCE, source.rootUri, error))
    }
    destination.errors.forEach { error ->
        add(syncScanDiagnosticLine(SyncSide.DESTINATION, destination.rootUri, error))
    }
    endpointError?.takeIf(String::isNotBlank)?.let { error ->
        add(syncScanDiagnosticLine(null, "", error))
    }
}.joinToString("\n").take(MAX_SYNC_SCAN_DIAGNOSTICS_LENGTH)

internal fun decodeSyncScanDiagnostics(encoded: String): List<SyncScanDiagnostic> =
    encoded.lineSequence().mapNotNull { line ->
        val fields = line.split('\t', limit = 3)
        if (fields.size != 3) return@mapNotNull null
        SyncScanDiagnostic(
            side = fields[0].takeIf(String::isNotBlank)?.let {
                runCatching { SyncSide.valueOf(it) }.getOrNull()
            },
            endpointUri = fields[1],
            detail = fields[2]
        )
    }.toList()

private fun syncScanDiagnosticLine(
    side: SyncSide?,
    endpointUri: String,
    detail: String
): String = listOf(
    side?.name.orEmpty(),
    endpointUri.replaceControlCharacters(),
    detail.replaceControlCharacters()
).joinToString("\t")

private fun String.replaceControlCharacters(): String =
    replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

private const val MAX_SYNC_SCAN_DIAGNOSTICS_LENGTH = 4_000
