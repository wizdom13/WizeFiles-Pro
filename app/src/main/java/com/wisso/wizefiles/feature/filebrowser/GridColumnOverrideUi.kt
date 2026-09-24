// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.view.Menu
import android.widget.RadioGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.wisso.wizefiles.R

class GridColumnOverrideUi(
    private val viewTypeGroup: RadioGroup,
    private val button: MaterialButton,
    initialValue: Int
) {
    var value: Int =
        if (initialValue in GridColumnOverrides.MANUAL_RANGE) {
            initialValue
        } else {
            GridColumnOverrides.AUTO
        }
        private set

    init {
        viewTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            button.isVisible = checkedId == R.id.view_type_grid
        }
        button.setOnClickListener { showOptions() }
        render()
    }

    private fun showOptions() {
        PopupMenu(button.context, button).apply {
            menu.add(
                Menu.NONE,
                AUTO_ITEM_ID,
                Menu.NONE,
                R.string.file_list_grid_columns_auto
            ).isChecked = value == GridColumnOverrides.AUTO
            GridColumnOverrides.MANUAL_RANGE.forEach { count ->
                menu.add(Menu.NONE, count, Menu.NONE, count.toString()).isChecked = value == count
            }
            menu.setGroupCheckable(Menu.NONE, true, true)
            setOnMenuItemClickListener { item ->
                value = if (item.itemId == AUTO_ITEM_ID) {
                    GridColumnOverrides.AUTO
                } else {
                    item.itemId
                }
                render()
                true
            }
            show()
        }
    }

    private fun render() {
        button.text = if (value == GridColumnOverrides.AUTO) {
            button.context.getString(R.string.file_list_grid_columns_auto)
        } else {
            value.toString()
        }
        button.isVisible = viewTypeGroup.checkedRadioButtonId == R.id.view_type_grid
    }

    private companion object {
        const val AUTO_ITEM_ID = 0x6A11
    }
}
