package com.wisso.wizefiles.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class TabFragmentPagerAdapter(
    fragment: Fragment,
    private vararg val tabs: Pair<CharSequence?, () -> Fragment>
) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment = tabs[position].second()

    fun getPageTitle(position: Int): CharSequence? = tabs[position].first
}
