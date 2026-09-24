package com.wisso.wizefiles.ui

import androidx.activity.OnBackPressedCallback
import com.leinardi.android.speeddial.SpeedDialView

class SpeedDialViewOnBackPressedCallback(
    private val speedDial: SpeedDialView
) : OnBackPressedCallback(speedDial.isOpen), SpeedDialView.OnChangeListener {
    init {
        speedDial.setOnChangeListener(this)
    }

    override fun onMainActionSelected(): Boolean = false

    override fun onToggleChanged(isOpen: Boolean) {
        isEnabled = isOpen
    }

    override fun handleOnBackPressed() {
        speedDial.close()
    }
}
