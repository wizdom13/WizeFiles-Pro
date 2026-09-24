package com.wisso.wizefiles.ui

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter

abstract class ViewPagerAdapter : PagerAdapter() {
    final override fun instantiateItem(container: ViewGroup, position: Int): Any =
        onCreateView(container, position)

    final override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        val view = item as? View
            ?: throw IllegalArgumentException("Pager item is not a View")
        onDestroyView(container, position, view)
        if (view.parent === container) container.removeView(view)
    }

    final override fun isViewFromObject(view: View, item: Any): Boolean = view === item

    final override fun getItemPosition(item: Any): Int =
        (item as? View)?.let(::getViewPosition) ?: POSITION_NONE

    protected abstract fun onCreateView(container: ViewGroup, position: Int): View
    protected abstract fun onDestroyView(container: ViewGroup, position: Int, view: View)
    protected open fun getViewPosition(view: View): Int = POSITION_UNCHANGED
}
