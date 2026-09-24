// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.view.DragEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.appcompat.widget.PopupMenu
import com.wisso.wizefiles.util.forceShowIcons
import androidx.core.view.isVisible
import java.nio.file.Path
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ItemBreadcrumbBinding
import com.wisso.wizefiles.util.getColorByAttr
import com.wisso.wizefiles.util.getColorStateListByAttr
import com.wisso.wizefiles.util.getDimensionPixelSize
import com.wisso.wizefiles.util.getResourceIdByAttr
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.withTheme

class BreadcrumbLayout : HorizontalScrollView {
    private val tabLayoutHeight = context.getDimensionPixelSize(R.dimen.tab_layout_height)
    // Using a color state list resource somehow results in red color in dark mode on API 21.
    // Run `git revert 5bb2fd1` once we no longer support API 21.
    private val itemColor =
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
            intArrayOf(
                context.getColorByAttr(android.R.attr.textColorPrimary),
                context.getColorByAttr(android.R.attr.textColorSecondary)
            )
        )
    private val locationIconColor = context.getColorStateListByAttr(
        com.google.android.material.R.attr.colorPrimary
    )
    private val popupContext = context.withTheme(
        context.getResourceIdByAttr(androidx.appcompat.R.attr.actionBarPopupTheme)
    )

    private val itemsLayout: LinearLayout

    private lateinit var listener: Listener
    private lateinit var data: BreadcrumbData

    private var isLayoutDirty = true
    private var isScrollToSelectedItemPending = false
    private var isFirstScroll = true

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(
        context, attrs
    )

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        isHorizontalScrollBarEnabled = false
        itemsLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        itemsLayout.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
        setPaddingRelative(0, 0, 0, 0)
        addView(itemsLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    override fun jumpDrawablesToCurrentState() {
        // HACK: AppBarLayout.updateAppBarLayoutDrawableState() calls
        // CoordinatorLayout.jumpDrawablesToCurrentState() to fix a pre-N visual bug according to a
        // comment in AppBarLayout.BaseBehavior.onLayoutChild(), however that results in our ripple
        // disappearing. One way to ignore that call path is to skip when we are in layout, so that
        // we at least preserve the other call path upon being attached to window.
        if (isInLayout) {
            return
        }
        super.jumpDrawablesToCurrentState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val newHeightMeasureSpec = if (heightMode != MeasureSpec.EXACTLY) {
            val maximumHeight = if (heightMode == MeasureSpec.AT_MOST) {
                MeasureSpec.getSize(heightMeasureSpec)
            } else {
                Int.MAX_VALUE
            }
            val height = tabLayoutHeight.coerceAtMost(maximumHeight)
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }

    override fun requestLayout() {
        isLayoutDirty = true

        super.requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        isLayoutDirty = false
        if (isScrollToSelectedItemPending) {
            scrollToSelectedItem()
            isScrollToSelectedItemPending = false
        }
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun setData(data: BreadcrumbData) {
        if (this::data.isInitialized && this.data == data) {
            return
        }
        this.data = data
        inflateItemViews()
        bindItemViews()
        scrollToSelectedItem()
    }

    private fun scrollToSelectedItem() {
        if (isLayoutDirty) {
            isScrollToSelectedItemPending = true
            return
        }
        val selectedItemView = itemsLayout.getChildAt(data.selectedIndex)
        val scrollX = if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            selectedItemView.left - itemsLayout.paddingStart
        } else {
            selectedItemView.right - width + itemsLayout.paddingStart
        }
        if (!isFirstScroll && isShown) {
            smoothScrollTo(scrollX, 0)
        } else {
            scrollTo(scrollX, 0)
        }
        isFirstScroll = false
    }

    private fun inflateItemViews() {
        // HACK: Remove/add views at the front so that ripple remains correct, as we are potentially
        // collapsing/expanding breadcrumbs at the front.
        for (index in data.paths.size..<itemsLayout.childCount) {
            itemsLayout.removeViewAt(0)
        }
        for (index in itemsLayout.childCount..<data.paths.size) {
            val binding = ItemBreadcrumbBinding.inflate(context.layoutInflater, itemsLayout, false)
            val menu = PopupMenu(popupContext, binding.root)
                .apply {
                    inflate(R.menu.menu_file_list_breadcrumb)
                    forceShowIcons()
                }
            binding.root.setOnLongClickListener {
                menu.show()
                true
            }
            binding.text.setTextColor(itemColor)
            binding.locationImage.imageTintList = locationIconColor
            binding.arrowImage.imageTintList = itemColor
            binding.root.tag = binding to menu
            itemsLayout.addView(binding.root, 0)
        }
    }

    private fun bindItemViews() {
        for (index in data.paths.indices) {
            @Suppress("UNCHECKED_CAST")
            val tag = itemsLayout.getChildAt(index).tag as Pair<ItemBreadcrumbBinding, PopupMenu>
            val (binding, menu) = tag
            binding.text.text = data.nameProducers[index](binding.text.context)
            val iconRes = data.iconResIds[index]
            binding.locationImage.isVisible = iconRes != null
            iconRes?.let(binding.locationImage::setImageResource)
            binding.arrowImage.isVisible = index != data.paths.size - 1
            binding.root.isActivated = index == data.selectedIndex
            val path = data.paths[index]
            binding.root.setOnClickListener {
                if (data.selectedIndex == index) {
                    scrollToSelectedItem()
                } else {
                    listener.navigateTo(path)
                }
            }
            binding.root.setOnDragListener { target, event ->
                if (event.action == DragEvent.ACTION_DRAG_LOCATION) {
                    val visibleStart = scrollX
                    val visibleEnd = scrollX + width
                    val step = (32 * resources.displayMetrics.density).toInt()
                    when {
                        target.left < visibleStart + step -> smoothScrollBy(-step, 0)
                        target.right > visibleEnd - step -> smoothScrollBy(step, 0)
                    }
                }
                listener.onBreadcrumbDrag(path, target, event)
            }
            menu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_copy_path -> {
                        listener.copyPath(path)
                        true
                    }
                    R.id.action_open_in_new_task -> {
                        listener.openInNewTask(path)
                        true
                    }
                    R.id.action_open_in_new_tab -> {
                        listener.openInNewTab(path)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    interface Listener {
        fun navigateTo(path: Path)
        fun copyPath(path: Path)
        fun openInNewTask(path: Path)
        fun openInNewTab(path: Path)
        fun onBreadcrumbDrag(path: Path, target: View, event: DragEvent): Boolean
    }
}
