package com.wisso.wizefiles.feature.filebrowser

import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R

/** Owns sort-dialog binding and translates widgets into a provider-neutral selection. */
internal object FileSortDialogController {
    data class State(
        val viewType: FileViewType,
        val sortOptions: FileSortOptions,
        val pathSpecific: Boolean,
        val gridColumns: Int
    )

    fun show(
        activity: AppCompatActivity,
        inflater: LayoutInflater,
        state: State,
        onSelected: (FileViewSortSelection) -> Unit
    ) {
        val view = inflater.inflate(R.layout.dialog_file_list_sort, null)
        val viewTypeGroup = view.findViewById<RadioGroup>(R.id.view_type_group)
        val sortByGroup = view.findViewById<RadioGroup>(R.id.sort_by_group)
        val sortDirectionGroup = view.findViewById<RadioGroup>(R.id.sort_direction_group)
        val directoriesFirst = view.findViewById<CheckBox>(R.id.show_folders_first)
        val pathSpecific = view.findViewById<CheckBox>(R.id.only_for_this_folder)
        val gridColumnsButton = view.findViewById<MaterialButton>(R.id.grid_columns_button)

        viewTypeGroup.check(state.viewType.toRadioId())
        sortByGroup.check(state.sortOptions.by.toRadioId())
        sortDirectionGroup.check(state.sortOptions.order.toRadioId())
        directoriesFirst.isChecked = state.sortOptions.isDirectoriesFirst
        pathSpecific.isChecked = state.pathSpecific
        val gridColumnsUi = GridColumnOverrideUi(viewTypeGroup, gridColumnsButton, state.gridColumns)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.file_list_action_view_sort)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSelected(
                    FileViewSortSelection(
                        viewType = viewTypeGroup.checkedRadioButtonId.toViewType(),
                        sortBy = sortByGroup.checkedRadioButtonId.toSortBy(),
                        sortOrder = sortDirectionGroup.checkedRadioButtonId.toSortOrder(),
                        directoriesFirst = directoriesFirst.isChecked,
                        pathSpecific = pathSpecific.isChecked,
                        gridColumns = gridColumnsUi.value
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun FileViewType.toRadioId(): Int = when (this) {
        FileViewType.LIST -> R.id.view_type_list
        FileViewType.GRID -> R.id.view_type_grid
    }

    internal fun FileSortOptions.By.toRadioId(): Int = when (this) {
        FileSortOptions.By.NAME -> R.id.sort_by_name
        FileSortOptions.By.TYPE -> R.id.sort_by_type
        FileSortOptions.By.SIZE -> R.id.sort_by_size
        FileSortOptions.By.LAST_MODIFIED -> R.id.sort_by_last_modified
    }

    internal fun FileSortOptions.Order.toRadioId(): Int = when (this) {
        FileSortOptions.Order.ASCENDING -> R.id.sort_direction_ascending
        FileSortOptions.Order.DESCENDING -> R.id.sort_direction_descending
    }

    internal fun Int.toViewType(): FileViewType =
        if (this == R.id.view_type_grid) FileViewType.GRID else FileViewType.LIST

    internal fun Int.toSortBy(): FileSortOptions.By = when (this) {
        R.id.sort_by_type -> FileSortOptions.By.TYPE
        R.id.sort_by_size -> FileSortOptions.By.SIZE
        R.id.sort_by_last_modified -> FileSortOptions.By.LAST_MODIFIED
        else -> FileSortOptions.By.NAME
    }

    internal fun Int.toSortOrder(): FileSortOptions.Order =
        if (this == R.id.sort_direction_descending) {
            FileSortOptions.Order.DESCENDING
        } else {
            FileSortOptions.Order.ASCENDING
        }
}
