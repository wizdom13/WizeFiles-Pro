// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.By
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.Order

internal fun sortVaultEntries(
    entries: List<VaultEntry>,
    options: FileSortOptions
): List<VaultEntry> = entries.sortedWith(vaultEntryComparator(options))

private fun vaultEntryComparator(options: FileSortOptions): Comparator<VaultEntry> {
    val nameComparator = Comparator<VaultEntry> { first, second ->
        String.CASE_INSENSITIVE_ORDER.compare(first.name, second.name)
    }.thenBy { it.name }
    var comparator = when (options.by) {
        By.NAME -> nameComparator
        By.TYPE -> compareBy<VaultEntry, String>(String.CASE_INSENSITIVE_ORDER) {
            if (it.isDirectory) "" else it.name.substringAfterLast('.', "")
        }.then(nameComparator)
        By.SIZE -> compareBy<VaultEntry> { it.size }.then(nameComparator)
        By.LAST_MODIFIED -> compareBy<VaultEntry> { it.modifiedAt }.then(nameComparator)
    }
    if (options.order == Order.DESCENDING) {
        comparator = comparator.reversed()
    }
    if (options.isDirectoriesFirst) {
        comparator = compareBy<VaultEntry> { !it.isDirectory }.then(comparator)
    }
    return comparator
}
