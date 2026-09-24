// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.view.ContextMenu
import android.view.Menu
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu

fun Menu.showOptionalIcons() {
    (this as? MenuBuilder)?.setOptionalIconsVisible(true)
}

fun PopupMenu.forceShowIcons() {
    menu.showOptionalIcons()
    setForceShowIcon(true)
}

fun ContextMenu.addIconItem(
    groupId: Int = Menu.NONE,
    itemId: Int = Menu.NONE,
    order: Int = Menu.NONE,
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int
) = add(groupId, itemId, order, titleRes).setIcon(iconRes)
