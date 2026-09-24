package com.wisso.wizefiles.vault

import android.widget.CheckBox
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.By
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.Order
import com.wisso.wizefiles.feature.filebrowser.FileViewType
import com.wisso.wizefiles.feature.filebrowser.GridColumnOverrideUi
import com.wisso.wizefiles.feature.filebrowser.GridLayoutPolicy
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat

internal class VaultViewOptionsController(
    private val activity: AppCompatActivity,
    private val availableGridWidthDp: () -> Int
) {
    fun show(sortOptions: FileSortOptions) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_file_list_sort, null)
        val viewTypeGroup = dialogView.findViewById<RadioGroup>(R.id.view_type_group)
        val sortByGroup = dialogView.findViewById<RadioGroup>(R.id.sort_by_group)
        val sortDirectionGroup = dialogView.findViewById<RadioGroup>(R.id.sort_direction_group)
        val showFoldersFirstCheckBox =
            dialogView.findViewById<CheckBox>(R.id.show_folders_first)
        val pathSpecificCheckBox =
            dialogView.findViewById<CheckBox>(R.id.only_for_this_folder)
        val gridColumnsButton =
            dialogView.findViewById<MaterialButton>(R.id.grid_columns_button)

        pathSpecificCheckBox.isVisible = false
        when (Settings.FILE_LIST_VIEW_TYPE.valueCompat) {
            FileViewType.LIST -> viewTypeGroup.check(R.id.view_type_list)
            FileViewType.GRID -> viewTypeGroup.check(R.id.view_type_grid)
        }
        when (sortOptions.by) {
            By.NAME -> sortByGroup.check(R.id.sort_by_name)
            By.TYPE -> sortByGroup.check(R.id.sort_by_type)
            By.SIZE -> sortByGroup.check(R.id.sort_by_size)
            By.LAST_MODIFIED -> sortByGroup.check(R.id.sort_by_last_modified)
        }
        when (sortOptions.order) {
            Order.ASCENDING -> sortDirectionGroup.check(R.id.sort_direction_ascending)
            Order.DESCENDING -> sortDirectionGroup.check(R.id.sort_direction_descending)
        }
        showFoldersFirstCheckBox.isChecked = sortOptions.isDirectoriesFirst
        val widthClass = GridLayoutPolicy.widthClass(availableGridWidthDp())
        val gridColumnsUi = GridColumnOverrideUi(
            viewTypeGroup,
            gridColumnsButton,
            Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.valueCompat.valueFor(widthClass)
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.file_list_action_view_sort)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedViewType =
                    if (viewTypeGroup.checkedRadioButtonId == R.id.view_type_grid) {
                        FileViewType.GRID
                    } else {
                        FileViewType.LIST
                    }
                val sortBy = when (sortByGroup.checkedRadioButtonId) {
                    R.id.sort_by_type -> By.TYPE
                    R.id.sort_by_size -> By.SIZE
                    R.id.sort_by_last_modified -> By.LAST_MODIFIED
                    else -> By.NAME
                }
                val sortOrder = when (sortDirectionGroup.checkedRadioButtonId) {
                    R.id.sort_direction_descending -> Order.DESCENDING
                    else -> Order.ASCENDING
                }

                Settings.FILE_LIST_VIEW_TYPE.putValue(selectedViewType)
                Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.putValue(
                    Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.valueCompat.withValue(
                        widthClass,
                        gridColumnsUi.value
                    )
                )
                Settings.FILE_LIST_SORT_OPTIONS.putValue(
                    FileSortOptions(
                        sortBy,
                        sortOrder,
                        showFoldersFirstCheckBox.isChecked
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
