package com.wisso.wizefiles.feature.filebrowser

internal data class BrowserSortDialogState(
    val viewType: FileViewType,
    val sortOptions: FileSortOptions,
    val pathSpecific: Boolean,
    val gridColumns: Int
)

internal object BrowserSortDialogPolicy {
    fun initial(
        viewType: FileViewType,
        sortOptions: FileSortOptions,
        pathSpecific: Boolean,
        gridColumns: Int
    ) = BrowserSortDialogState(viewType, sortOptions, pathSpecific, gridColumns)

    fun normalized(state: BrowserSortDialogState): BrowserSortDialogState = state.copy(
        gridColumns = if (state.gridColumns in GridColumnOverrides.MANUAL_RANGE) {
            state.gridColumns
        } else GridColumnOverrides.AUTO
    )
}

internal object BrowserSelectionReducer {
    fun <T> reduce(current: Set<T>, requested: Set<T>, selected: Boolean): Set<T> =
        if (selected) current + requested else current - requested
}
