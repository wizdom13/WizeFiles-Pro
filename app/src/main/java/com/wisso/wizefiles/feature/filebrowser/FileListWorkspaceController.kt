package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.ui.PersistentDrawerLayout

internal class FileListWorkspaceController {
    data class Views(
        val contentLayout: ViewGroup,
        val primaryBreadcrumb: BreadcrumbLayout,
        val persistentDrawerLayout: PersistentDrawerLayout?,
        val recyclerView: RecyclerView
    )

    private val coordinator = BrowserWorkspaceCoordinator()
    private var context: Context? = null
    private var views: Views? = null
    private var onDividerFractionChanged: ((Float) -> Unit)? = null
    private var onSecondaryPaneTouched: (() -> Unit)? = null

    var paneLayout: BrowserPaneLayout? = null
        private set

    var secondaryBreadcrumb: BreadcrumbLayout? = null
        private set

    val activePane: BrowserPane
        get() = coordinator.activePane

    val dualPaneVisible: Boolean
        get() = coordinator.dualPaneVisible

    fun bind(
        context: Context,
        views: Views,
        onDividerFractionChanged: (Float) -> Unit,
        onSecondaryPaneTouched: () -> Unit
    ) {
        release()
        this.context = context
        this.views = views
        this.onDividerFractionChanged = onDividerFractionChanged
        this.onSecondaryPaneTouched = onSecondaryPaneTouched
    }

    fun release() {
        paneLayout?.onDividerFractionChanged = null
        secondaryBreadcrumb?.setOnTouchListener(null)
        onSecondaryPaneTouched = null
        onDividerFractionChanged = null
        secondaryBreadcrumb = null
        paneLayout = null
        views = null
        context = null
    }

    fun ensureLayout() {
        if (paneLayout != null) return
        val context = requireNotNull(context)
        val views = requireNotNull(views)
        val content = views.contentLayout
        val parent = content.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(content)
        val originalLayoutParams = content.layoutParams
        parent.removeViewAt(index)
        paneLayout = BrowserPaneLayout(context).apply {
            id = R.id.browserPaneLayout
            layoutParams = originalLayoutParams
            setPrimaryView(content)
            onDividerFractionChanged = { onDividerFractionChanged?.invoke(it) }
        }.also { parent.addView(it, index) }
        ensureBreadcrumbRow()
    }

    fun updatePresentation(
        dualPaneVisible: Boolean,
        requestedActivePane: BrowserPane,
        dividerFraction: Float,
        verticalHingeBounds: Rect?,
        persistentDrawerAllowed: Boolean
    ): BrowserWorkspaceChange {
        ensureLayout()
        val change = coordinator.updateVisibility(dualPaneVisible, requestedActivePane)
        paneLayout?.apply {
            this.dividerFraction = dividerFraction
            activePane = change.activePane
            setVerticalHinge(verticalHingeBounds)
            secondaryVisible = change.secondaryPaneRequired
        }
        secondaryBreadcrumb?.isVisible = change.secondaryPaneRequired
        if (!persistentDrawerAllowed) {
            views?.persistentDrawerLayout?.closeDrawer(GravityCompat.START)
        }
        return change
    }

    fun activate(pane: BrowserPane): Boolean {
        val changed = coordinator.activate(pane)
        if (changed) paneLayout?.activePane = coordinator.activePane
        return changed
    }

    fun synchronizeActivePane(pane: BrowserPane) {
        coordinator.synchronizeActivePane(pane)
        paneLayout?.activePane = coordinator.activePane
    }

    fun active(primary: FileListFragment, secondary: FileListFragment?): FileListFragment =
        coordinator.active(primary, secondary)

    fun other(primary: FileListFragment, secondary: FileListFragment?): FileListFragment? =
        coordinator.other(primary, secondary)

    fun bindSecondaryBreadcrumb(pane: FileListFragment, data: BreadcrumbData?) {
        secondaryBreadcrumb?.setListener(pane)
        data?.let { secondaryBreadcrumb?.setData(it) }
    }

    fun autoScrollDuringDrag(target: View, event: android.view.DragEvent) {
        val views = views ?: return
        val recycler = views.recyclerView
        if (!recycler.isShown || recycler.height == 0) return
        var ancestor: View? = target
        var isPaneListTarget = false
        while (ancestor != null) {
            if (ancestor === recycler || ancestor === views.contentLayout) {
                isPaneListTarget = true
                break
            }
            ancestor = ancestor.parent as? View
        }
        if (!isPaneListTarget) return
        val targetLocation = IntArray(2)
        val recyclerLocation = IntArray(2)
        target.getLocationOnScreen(targetLocation)
        recycler.getLocationOnScreen(recyclerLocation)
        val relativeY = targetLocation[1] - recyclerLocation[1] + event.y
        val edge = recycler.height * 0.16f
        val step = (24 * requireNotNull(context).resources.displayMetrics.density).toInt()
        when {
            relativeY < edge -> recycler.scrollBy(0, -step)
            relativeY > recycler.height - edge -> recycler.scrollBy(0, step)
        }
    }

    private fun ensureBreadcrumbRow() {
        if (secondaryBreadcrumb != null) return
        val context = requireNotNull(context)
        val primary = requireNotNull(views).primaryBreadcrumb
        val parent = primary.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(primary)
        val originalLayoutParams = primary.layoutParams
        parent.removeViewAt(index)
        val row = LinearLayout(context).apply {
            id = R.id.browserBreadcrumbRow
            orientation = LinearLayout.HORIZONTAL
            layoutParams = originalLayoutParams
        }
        primary.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        row.addView(primary)
        secondaryBreadcrumb = BreadcrumbLayout(context).apply {
            id = R.id.browserSecondaryBreadcrumb
            isVisible = false
            setPaddingRelative(
                primary.paddingStart,
                primary.paddingTop,
                primary.paddingEnd,
                primary.paddingBottom
            )
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onSecondaryPaneTouched?.invoke()
                }
                false
            }
        }
        row.addView(
            secondaryBreadcrumb,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        parent.addView(row, index)
    }
}
