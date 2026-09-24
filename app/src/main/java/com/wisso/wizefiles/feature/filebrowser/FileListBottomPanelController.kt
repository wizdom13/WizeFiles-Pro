// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.wisso.wizefiles.R
import com.wisso.wizefiles.ui.ToolbarActionMode
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.util.asFileName
import com.wisso.wizefiles.util.setOnEditorConfirmActionListener

internal class FileListBottomPanelController {
    data class Views(
        val bottomToolbar: Toolbar,
        val createFileNameEdit: EditText,
        val selectionActionLayout: ViewGroup,
        val primaryActions: List<MaterialButton>,
        val moreAction: MaterialButton
    )

    data class State(
        val selectionCount: Int,
        val selectionContainsDirectory: Boolean,
        val pickerMode: PickOptions.Mode?,
        val pickerFileName: String?,
        val initializeCreateFileName: Boolean,
        val inRecycleBin: Boolean,
        val pasteCopy: Boolean,
        val pasteFileCount: Int,
        val pasteContainsOnlyArchivePaths: Boolean,
        val pasteAvailable: Boolean,
        val directoryConfirmationLabel: CharSequence?
    )

    private val selectionPanelController = FileListSelectionPanelController()
    private var context: Context? = null
    private var actionMode: ToolbarActionMode? = null
    private var views: Views? = null
    private var onNavigationClicked: (() -> Unit)? = null
    private var onMenuItemClicked: ((MenuItem) -> Boolean)? = null
    private var onPanelVisibilityChanged: ((Boolean) -> Unit)? = null

    fun bind(
        context: Context,
        actionMode: ToolbarActionMode,
        views: Views,
        onSelectionActionClicked: (Int) -> Boolean,
        onNavigationClicked: () -> Unit,
        onMenuItemClicked: (MenuItem) -> Boolean,
        onPanelVisibilityChanged: (Boolean) -> Unit
    ) {
        release()
        this.context = context
        this.actionMode = actionMode
        this.views = views
        this.onNavigationClicked = onNavigationClicked
        this.onMenuItemClicked = onMenuItemClicked
        this.onPanelVisibilityChanged = onPanelVisibilityChanged
        selectionPanelController.bind(
            context,
            FileListSelectionPanelController.Views(
                bottomToolbar = views.bottomToolbar,
                createFileNameEdit = views.createFileNameEdit,
                actionLayout = views.selectionActionLayout,
                primaryActions = views.primaryActions,
                moreAction = views.moreAction
            ),
            onSelectionActionClicked
        )
    }

    fun render(
        state: State,
        configureSelectionMenu: (Menu) -> Unit,
        onCreateFileNameInitialized: () -> Unit,
        onDirectoryConfirm: () -> Unit
    ) {
        val actionMode = requireNotNull(actionMode)
        val views = requireNotNull(views)
        views.bottomToolbar.setOnClickListener(null)
        views.bottomToolbar.contentDescription = null
        views.createFileNameEdit.setOnEditorActionListener(null)

        when (
            val content = FileListBottomPanelCoordinator.resolveContent(
                hasSelection = state.selectionCount > 0,
                pickerMode = state.pickerMode,
                hasPasteFiles = state.pasteFileCount > 0
            )
        ) {
            FileListBottomPanelCoordinator.Content.Selection -> {
                actionMode.setMenuResource(0)
                selectionPanelController.show(
                    menuResource = if (state.pickerMode != null) {
                        R.menu.menu_file_list_pick
                    } else {
                        R.menu.menu_file_list_select
                    },
                    selectionCount = state.selectionCount,
                    containsDirectory = state.selectionContainsDirectory,
                    pickerMode = state.pickerMode,
                    inRecycleBin = state.inRecycleBin,
                    configureMenu = configureSelectionMenu
                )
            }
            FileListBottomPanelCoordinator.Content.Hidden -> {
                selectionPanelController.showToolbarContent()
                finishOrHidePanel()
                return
            }
            FileListBottomPanelCoordinator.Content.Paste -> {
                selectionPanelController.showToolbarContent()
                showPastePanel(state)
            }
            is FileListBottomPanelCoordinator.Content.Picker -> {
                selectionPanelController.showToolbarContent()
                actionMode.setMenuResource(R.menu.menu_file_list_pick_bottom)
                val menu = actionMode.menu
                when (content.mode) {
                    PickOptions.Mode.CREATE_FILE -> {
                        actionMode.title = null
                        views.createFileNameEdit.isVisible = true
                        val createMenuItem = menu.findItem(R.id.action_create)
                        views.createFileNameEdit.setOnEditorConfirmActionListener {
                            onMenuItemClicked?.invoke(createMenuItem) == true
                        }
                        if (state.initializeCreateFileName) {
                            val fileName = requireNotNull(state.pickerFileName)
                            views.createFileNameEdit.setText(fileName)
                            views.createFileNameEdit.setSelection(
                                0,
                                fileName.asFileName().baseName.length
                            )
                            views.createFileNameEdit.requestFocus()
                            onCreateFileNameInitialized()
                        }
                        createMenuItem.isVisible = true
                    }
                    PickOptions.Mode.OPEN_DIRECTORY -> {
                        val confirmationLabel = requireNotNull(state.directoryConfirmationLabel)
                        actionMode.title = confirmationLabel
                        views.createFileNameEdit.isVisible = false
                        views.bottomToolbar.apply {
                            contentDescription = confirmationLabel
                            setOnClickListener {
                                AppLog.i(
                                    DIRECTORY_PICKER_LOG_TAG,
                                    "Confirmation tapped from the full bottom toolbar"
                                )
                                onDirectoryConfirm()
                            }
                        }
                        menu.findItem(R.id.action_create).isVisible = false
                    }
                    PickOptions.Mode.OPEN_FILE -> {
                        finishOrHidePanel()
                        return
                    }
                }
            }
        }
        ensureStarted()
    }

    fun performShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val actionMode = actionMode ?: return false
        if (!actionMode.isActive) return false
        return actionMode.menu.run {
            setQwertyMode(
                KeyCharacterMap.load(event.deviceId).keyboardType != KeyCharacterMap.NUMERIC
            )
            performShortcut(keyCode, event, 0)
        }
    }

    fun finish() {
        actionMode?.finish()
    }

    fun release() {
        actionMode?.finish(animate = false)
        views?.bottomToolbar?.setOnClickListener(null)
        views?.createFileNameEdit?.setOnEditorActionListener(null)
        selectionPanelController.release()
        onPanelVisibilityChanged?.invoke(false)
        onPanelVisibilityChanged = null
        onMenuItemClicked = null
        onNavigationClicked = null
        views = null
        actionMode = null
        context = null
    }

    private fun showPastePanel(state: State) {
        val context = requireNotNull(context)
        val actionMode = requireNotNull(actionMode)
        val views = requireNotNull(views)
        val presentation = FileListBottomPanelCoordinator.resolvePastePresentation(
            copy = state.pasteCopy,
            containsOnlyArchivePaths = state.pasteContainsOnlyArchivePaths
        )
        actionMode.title = context.getString(presentation.titleRes, state.pasteFileCount)
        views.createFileNameEdit.isVisible = false
        actionMode.setMenuResource(R.menu.menu_file_list_paste)
        actionMode.menu.findItem(R.id.action_paste).apply {
            setIcon(presentation.actionIconRes)
            setTitle(presentation.actionTitleRes)
            isVisible = state.pasteAvailable
        }
    }

    private fun finishOrHidePanel() {
        val actionMode = requireNotNull(actionMode)
        if (actionMode.isActive) {
            actionMode.finish()
        } else {
            onPanelVisibilityChanged?.invoke(false)
        }
    }

    private fun ensureStarted() {
        val actionMode = requireNotNull(actionMode)
        onPanelVisibilityChanged?.invoke(true)
        if (!actionMode.isActive) {
            actionMode.start(object : ToolbarActionMode.Callback {
                override fun onToolbarNavigationIconClicked(toolbarActionMode: ToolbarActionMode) {
                    onNavigationClicked?.invoke()
                }

                override fun onToolbarActionModeMenuItemClicked(
                    toolbarActionMode: ToolbarActionMode,
                    item: MenuItem
                ): Boolean = onMenuItemClicked?.invoke(item) == true

                override fun onToolbarActionModeFinished(toolbarActionMode: ToolbarActionMode) {
                    selectionPanelController.showToolbarContent()
                    onPanelVisibilityChanged?.invoke(false)
                }
            })
        }
    }
}

private const val DIRECTORY_PICKER_LOG_TAG = "DirectoryPicker"
