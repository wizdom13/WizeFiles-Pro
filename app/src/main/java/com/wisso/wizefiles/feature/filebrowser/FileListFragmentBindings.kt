// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.leinardi.android.speeddial.SpeedDialView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.FragmentFileListBinding
import com.wisso.wizefiles.databinding.IncludeFileListAppBarBinding
import com.wisso.wizefiles.databinding.IncludeFileListBottomBarBinding
import com.wisso.wizefiles.databinding.IncludeFileListContentBinding
import com.wisso.wizefiles.databinding.IncludeFileListBinding
import com.wisso.wizefiles.databinding.IncludeFileListSpeedDialBinding
import com.wisso.wizefiles.ui.CoordinatorAppBarLayout
import com.wisso.wizefiles.ui.PersistentBarLayout
import com.wisso.wizefiles.ui.PersistentDrawerLayout

internal class FileListFragmentBinding private constructor(
    val root: View,
    val drawerLayout: DrawerLayout? = null,
    val persistentDrawerLayout: PersistentDrawerLayout? = null,
    val persistentBarLayout: PersistentBarLayout,
    val appBarLayout: CoordinatorAppBarLayout,
    val toolbar: Toolbar,
    val overlayToolbar: Toolbar,
    val browserTabLayout: TabLayout,
    val breadcrumbLayout: BreadcrumbLayout,
    val contentLayout: ViewGroup,
    val progress: ProgressBar,
    val errorText: TextView,
    val emptyView: View,
    val swipeRefreshLayout: SwipeRefreshLayout,
    val recyclerView: RecyclerView,
    val bottomBarLayout: ViewGroup,
    val bottomToolbar: Toolbar,
    val bottomCreateFileNameEdit: EditText,
    val selectionActionLayout: ViewGroup,
    val selectionPrimaryAction1: MaterialButton,
    val selectionPrimaryAction2: MaterialButton,
    val selectionPrimaryAction3: MaterialButton,
    val selectionPrimaryAction4: MaterialButton,
    val selectionMoreAction: MaterialButton,
    val speedDialView: SpeedDialView
) {
    companion object {
        fun inflate(
            inflater: LayoutInflater,
            root: ViewGroup?,
            attachToRoot: Boolean
        ): FileListFragmentBinding {
            val binding = FragmentFileListBinding.inflate(inflater, root, attachToRoot)
            val bindingRoot = binding.root
            val includeBinding = IncludeFileListBinding.bind(bindingRoot)
            val appBarBinding = IncludeFileListAppBarBinding.bind(bindingRoot)
            val contentBinding = IncludeFileListContentBinding.bind(bindingRoot)
            val bottomBarBinding = IncludeFileListBottomBarBinding.bind(bindingRoot)
            val speedDialBinding = IncludeFileListSpeedDialBinding.bind(bindingRoot)
            return FileListFragmentBinding(
                bindingRoot, includeBinding.drawerLayout, includeBinding.persistentDrawerLayout,
                includeBinding.persistentBarLayout, appBarBinding.appBarLayout,
                appBarBinding.toolbar, appBarBinding.overlayToolbar,
                appBarBinding.browserTabLayout, appBarBinding.breadcrumbLayout,
                contentBinding.contentLayout,
                contentBinding.progress, contentBinding.errorText, contentBinding.emptyView,
                contentBinding.swipeRefreshLayout, contentBinding.recyclerView,
                bottomBarBinding.bottomBarLayout, bottomBarBinding.bottomToolbar,
                bottomBarBinding.bottomCreateFileNameEdit,
                bottomBarBinding.selectionActionLayout,
                bottomBarBinding.selectionPrimaryAction1,
                bottomBarBinding.selectionPrimaryAction2,
                bottomBarBinding.selectionPrimaryAction3,
                bottomBarBinding.selectionPrimaryAction4,
                bottomBarBinding.selectionMoreAction,
                speedDialBinding.speedDialView
            )
        }
    }
}

internal class FileListFragmentMenuBinding private constructor(
    val menu: Menu,
    val searchItem: MenuItem,
    val viewSortItem: MenuItem,
    val selectAllItem: MenuItem,
    val showHiddenFilesItem: MenuItem
) {
    companion object {
        fun inflate(menu: Menu, inflater: MenuInflater): FileListFragmentMenuBinding {
            inflater.inflate(R.menu.menu_file_list, menu)
            return FileListFragmentMenuBinding(
                menu, menu.findItem(R.id.action_search), menu.findItem(R.id.action_view_sort),
                menu.findItem(R.id.action_select_all),
                menu.findItem(R.id.action_show_hidden_files)
            )
        }
    }
}
