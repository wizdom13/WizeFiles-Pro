package com.wisso.wizefiles.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Checkable
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes

open class CheckableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes), Checkable {
    private var checkedState = false

    override fun isChecked(): Boolean = checkedState

    override fun setChecked(checked: Boolean) {
        if (checkedState == checked) return
        checkedState = checked
        refreshDrawableState()
    }

    override fun toggle() = setChecked(!checkedState)

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val state = super.onCreateDrawableState(extraSpace + if (checkedState) 1 else 0)
        if (checkedState) mergeDrawableStates(state, CHECKED_STATE)
        return state
    }

    private companion object {
        val CHECKED_STATE = intArrayOf(android.R.attr.state_checked)
    }
}
