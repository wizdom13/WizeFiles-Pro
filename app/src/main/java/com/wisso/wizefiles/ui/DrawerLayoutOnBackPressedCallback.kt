// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

class DrawerLayoutOnBackPressedCallback(
    private val drawer: DrawerLayout,
    private val gravity: Int = GravityCompat.START
) : OnBackPressedCallback(false) {
    private val stateListener = object : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerOpened(drawerView: View) = updateEnabledState()
        override fun onDrawerClosed(drawerView: View) = updateEnabledState()
    }

    init {
        drawer.addDrawerListener(stateListener)
        updateEnabledState()
    }

    override fun handleOnBackPressed() {
        if (isEnabled) drawer.closeDrawer(gravity)
    }

    private fun updateEnabledState() {
        isEnabled = drawer.isDrawerVisible(gravity) &&
            drawer.getDrawerLockMode(gravity) == DrawerLayout.LOCK_MODE_UNLOCKED
    }
}
