// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

internal data class FileViewSortSelection(
    val viewType: FileViewType,
    val sortBy: FileSortOptions.By,
    val sortOrder: FileSortOptions.Order,
    val directoriesFirst: Boolean,
    val pathSpecific: Boolean,
    val gridColumns: Int
)

/** Applies one immutable dialog result so every persisted field uses the same routing decision. */
internal object FileViewSortSelectionApplier {
    fun apply(
        selection: FileViewSortSelection,
        widthClass: GridWidthClass,
        setPathSpecificMode: (Boolean) -> Unit,
        setViewType: (FileViewType, Boolean) -> Unit,
        setGridColumns: (GridWidthClass, Int, Boolean) -> Unit,
        setSortBy: (FileSortOptions.By, Boolean) -> Unit,
        setSortOrder: (FileSortOptions.Order, Boolean) -> Unit,
        setDirectoriesFirst: (Boolean, Boolean) -> Unit
    ) {
        val pathSpecific = selection.pathSpecific
        setPathSpecificMode(pathSpecific)
        setViewType(selection.viewType, pathSpecific)
        setGridColumns(widthClass, selection.gridColumns, pathSpecific)
        setSortBy(selection.sortBy, pathSpecific)
        setSortOrder(selection.sortOrder, pathSpecific)
        setDirectoriesFirst(selection.directoriesFirst, pathSpecific)
    }
}

internal enum class FileViewSortPersistenceTarget { GLOBAL, PATH }

internal object FileViewSortPersistencePolicy {
    fun target(pathSpecific: Boolean): FileViewSortPersistenceTarget =
        if (pathSpecific) FileViewSortPersistenceTarget.PATH
        else FileViewSortPersistenceTarget.GLOBAL

    fun <T : Any> effective(pathSpecific: Boolean, pathValue: T?, globalValue: T): T =
        if (pathSpecific) pathValue ?: globalValue else globalValue

    fun <T : Any> initializeOverride(currentValue: T?, globalValue: T): T =
        currentValue ?: globalValue
}
