package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.wisso.wizefiles.util.showOptionalIcons

internal class FileListSelectionPanelController {
    data class Views(
        val bottomToolbar: Toolbar,
        val createFileNameEdit: EditText,
        val actionLayout: ViewGroup,
        val primaryActions: List<MaterialButton>,
        val moreAction: MaterialButton
    )

    private var context: Context? = null
    private var views: Views? = null
    private var onActionClicked: ((Int) -> Boolean)? = null
    private var popupMenu: PopupMenu? = null
    private var quickActionIds = IntArray(0)

    fun bind(context: Context, views: Views, onActionClicked: (Int) -> Boolean) {
        release()
        this.context = context
        this.views = views
        this.onActionClicked = onActionClicked
        quickActionIds = IntArray(views.primaryActions.size)
        views.primaryActions.forEachIndexed { index, button ->
            button.setOnClickListener { performQuickAction(index) }
        }
        views.moreAction.setOnClickListener { popupMenu?.show() }
    }

    fun show(
        menuResource: Int,
        selectionCount: Int,
        containsDirectory: Boolean,
        pickerMode: PickOptions.Mode?,
        inRecycleBin: Boolean,
        configureMenu: (Menu) -> Unit
    ) {
        val context = requireNotNull(context)
        val views = requireNotNull(views)
        popupMenu?.dismiss()
        views.bottomToolbar.isVisible = false
        views.actionLayout.isVisible = true
        views.createFileNameEdit.isVisible = false

        val popup = PopupMenu(context, views.moreAction).apply {
            inflate(menuResource)
            menu.showOptionalIcons()
        }
        configureMenu(popup.menu)
        val actionIds = FileListBottomPanelCoordinator.primarySelectionActionIds(
            selectionCount = selectionCount,
            containsDirectory = containsDirectory,
            pickerMode = pickerMode,
            inRecycleBin = inRecycleBin
        )
        val primaryItems = actionIds
            .mapNotNull { popup.menu.findItem(it) }
            .filter { it.isVisible }
            .take(views.primaryActions.size)
        quickActionIds.fill(0)
        views.primaryActions.forEachIndexed { index, button ->
            val item = primaryItems.getOrNull(index)
            if (item == null) {
                button.isVisible = false
            } else {
                quickActionIds[index] = item.itemId
                bindQuickAction(button, item)
                item.isVisible = false
            }
        }
        views.moreAction.isEnabled =
            (0 until popup.menu.size()).any { popup.menu.getItem(it).isVisible }
        popup.setOnMenuItemClickListener { item ->
            onActionClicked?.invoke(item.itemId) == true
        }
        popupMenu = popup
    }

    fun showToolbarContent() {
        popupMenu?.dismiss()
        popupMenu = null
        views?.actionLayout?.isVisible = false
        views?.bottomToolbar?.isVisible = true
    }

    fun release() {
        popupMenu?.dismiss()
        popupMenu = null
        views?.primaryActions?.forEach { it.setOnClickListener(null) }
        views?.moreAction?.setOnClickListener(null)
        quickActionIds = IntArray(0)
        onActionClicked = null
        views = null
        context = null
    }

    private fun performQuickAction(index: Int) {
        val itemId = quickActionIds.getOrNull(index) ?: return
        if (itemId != 0) {
            onActionClicked?.invoke(itemId)
        }
    }

    private fun bindQuickAction(button: MaterialButton, item: MenuItem) {
        button.icon = item.icon
        button.text = item.title
        button.contentDescription = item.title
        button.isEnabled = item.isEnabled
        button.isVisible = true
    }
}
