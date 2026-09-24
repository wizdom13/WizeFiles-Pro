// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.graphics.Rect
import android.text.TextUtils
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.animation.Interpolator
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.leinardi.android.speeddial.SpeedDialView
import com.google.android.material.tabs.TabLayout
import java.nio.file.Path
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.batchrename.BatchRenameActivity
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.batchrename.BatchRenameSessionStore
import com.wisso.wizefiles.databinding.IncludeFileListAppBarBinding
import com.wisso.wizefiles.databinding.FragmentFileListBinding
import com.wisso.wizefiles.databinding.IncludeFileListBottomBarBinding
import com.wisso.wizefiles.databinding.IncludeFileListContentBinding
import com.wisso.wizefiles.databinding.IncludeFileListBinding
import com.wisso.wizefiles.databinding.IncludeFileListSpeedDialBinding
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.filejobs.DeleteOptions
import com.wisso.wizefiles.viewer.text.TextEditorActivity
import com.wisso.wizefiles.feature.details.FilePropertiesDialogFragment
import com.wisso.wizefiles.navigation.NavigationAction
import com.wisso.wizefiles.navigation.NavigationFragment
import com.wisso.wizefiles.navigation.findNavigationRoot
import com.wisso.wizefiles.navigation.NavigationRootMapLiveData
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.archive.editor.ArchiveEditCapabilities
import com.wisso.wizefiles.provider.content.isContentPath
import com.wisso.wizefiles.provider.sftp.client.SftpHostKeyMismatchException
import com.wisso.wizefiles.provider.sftp.client.SftpHostKeyTrustStore
import com.wisso.wizefiles.provider.sftp.client.SftpHostKeyVerificationException
import com.wisso.wizefiles.provider.sftp.client.SftpPresentedHostKey
import com.wisso.wizefiles.provider.sftp.client.SftpUnknownHostKeyException
import com.wisso.wizefiles.recyclebin.RecycleBinManager
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.EditRcloneStorageActivity
import com.wisso.wizefiles.storage.EditRcloneStorageFragment
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.storagecleaner.StorageCleanerActivity
import com.wisso.wizefiles.feature.transfer.TransferCenterActivity
import com.wisso.wizefiles.feature.sync.SyncProfilesActivity
import com.wisso.wizefiles.feature.share.LocalShareActivity
import com.wisso.wizefiles.feature.nearby.NearbyTransferActivity
import com.wisso.wizefiles.ui.AppBarLayoutExpandHackListener
import com.wisso.wizefiles.ui.CoordinatorAppBarLayout
import com.wisso.wizefiles.ui.DrawerLayoutOnBackPressedCallback
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.ui.OverlayToolbarActionMode
import com.wisso.wizefiles.ui.PersistentBarLayout
import com.wisso.wizefiles.ui.PersistentBarLayoutToolbarActionMode
import com.wisso.wizefiles.ui.PersistentDrawerLayout
import com.wisso.wizefiles.ui.RecyclerViewFastScrollPopup
import com.wisso.wizefiles.ui.ScrollingViewOnApplyWindowInsetsListener
import com.wisso.wizefiles.ui.SpeedDialViewOnBackPressedCallback
import com.wisso.wizefiles.ui.ToolbarActionMode
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.showOptionalIcons
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.addOnBackPressedCallback
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.asFileName
import com.wisso.wizefiles.util.asFileNameOrNull
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.fadeToVisibilityUnsafe
import com.wisso.wizefiles.util.getDimensionDp
import com.wisso.wizefiles.util.getQuantityString
import com.wisso.wizefiles.util.hasSw600Dp
import com.wisso.wizefiles.util.isOrientationLandscape
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.vault.AddVaultDialogActivity
import kotlin.math.roundToInt

class FileListFragment : FileListPermissionFragment(), BreadcrumbLayout.Listener, FileListAdapter.Listener,
    ConfirmReplaceFileDialogFragment.Listener, OpenApkDialogFragment.Listener,
    ConfirmDeleteFilesDialogFragment.Listener, CreateArchiveDialogFragment.Listener,
    EncryptFilesDialogFragment.Listener, DecryptFilesDialogFragment.Listener,
    RenameFileDialogFragment.Listener, CreateFileDialogFragment.Listener,
    CreateDirectoryDialogFragment.Listener, NavigateToPathDialogFragment.Listener,
    NavigationFragment.Listener, BrowserCommandTarget {
    private val args by args<Args>()
    private val argsPath by lazy { args.intent.extraPath }
    private val isEmbeddedPane: Boolean
        get() = args.embeddedPane

    private val viewModel by viewModels<FileListViewModel>()
    override val permissionViewModel:FileListViewModel
        get()=viewModel

    override fun refreshAfterPermissionChange() {
        refresh()
    }

    private lateinit var binding: FileListFragmentBinding

    private lateinit var navigationFragment: NavigationFragment

    private lateinit var menuBinding: FileListFragmentMenuBinding

    private lateinit var overlayActionMode: ToolbarActionMode

    private val bottomPanelController = FileListBottomPanelController()

    private val operationController by lazy { FileListOperationController(viewModel) }

    private val operationLauncher by lazy {
        BrowserOperationLauncher(operationController) { files, mode, secureShred, options ->
            ConfirmDeleteFilesDialogFragment.show(files, mode, secureShred, options, this)
        }
    }

    private val externalActionController by lazy { FileListExternalActionController(viewModel) }

    private val sftpHostKeyDialogController by lazy {
        FileListSftpHostKeyDialogController(this, ::refresh)
    }

    private lateinit var layoutManager: GridLayoutManager

    private lateinit var adapter: FileListAdapter

    private val renderCoordinator = FileListRenderCoordinator(::calculateSpanCount)

    private val browserCommandCoordinator = BrowserCommandCoordinator(
        context = ::browserCommandContext,
        copy = {
            activePaneFragment().let { it.copyFiles(it.viewModel.selectedFiles) }
        },
        cut = {
            activePaneFragment().let { it.cutFiles(it.viewModel.selectedFiles) }
        },
        paste = {
            activePaneFragment().let { it.pasteFiles(it.currentPath) }
        },
        delete = {
            activePaneFragment().let { it.confirmDeleteFiles(it.viewModel.selectedFiles) }
        },
        rename = {
            activePaneFragment().let {
                RenameFileDialogFragment.show(it.viewModel.selectedFiles.single(), it)
            }
        },
        copyToOtherPane = { transferSelectionToOtherPane(copy = true) },
        moveToOtherPane = { transferSelectionToOtherPane(copy = false) }
    )

    private val navigationCoordinator by lazy {
        BrowserNavigationCoordinator(
            currentPath = { viewModel.currentPathLiveData.value },
            storageRoot = { path ->
                findNavigationRoot(path, NavigationRootMapLiveData.valueCompat)?.path
            },
            hasSelection = { viewModel.selectedFiles.isNotEmpty() },
            clearSelection = viewModel::clearSelectedFiles,
            collapseSearch = ::collapseSearchView,
            navigateUp = viewModel::navigateUp,
            navigateTo = { path ->
                viewModel.navigateTo(requireNotNull(layoutManager.onSaveInstanceState()), path)
            }
        )
    }

    private val selectionCoordinator by lazy {
        BrowserSelectionCoordinator(
            selection = { activePaneFragment().viewModel.selectedFiles },
            clearSelection = { activePaneFragment().viewModel.clearSelectedFiles() },
            dispatchCommand = ::dispatchBrowserCommand,
            effects = BrowserSelectionCoordinator.Effects(
                open = { activePaneFragment().pickFiles(it) },
                create = { activePaneFragment().confirmReplaceFile(it) },
                openWith = { activePaneFragment().openFileWith(it) },
                edit = { activePaneFragment().startActivity(TextEditorActivity.createIntent(it.path)) },
                restore = { activePaneFragment().restoreFiles(it) },
                extract = { activePaneFragment().extractFiles(it) },
                archive = { activePaneFragment().showCreateArchiveDialog(it) },
                encrypt = { activePaneFragment().showEncryptDialog(it) },
                decrypt = { activePaneFragment().showDecryptDialog(it) },
                share = { activePaneFragment().shareFiles(it) },
                sendNearby = { files ->
                    val target = activePaneFragment()
                    target.startActivity(
                        NearbyTransferActivity.createIntent(
                            target.requireContext(),
                            files.map { it.path }
                        )
                    )
                    target.viewModel.selectFiles(files, false)
                },
                batchRename = { activePaneFragment().openBatchRename(it) },
                copyPath = { activePaneFragment().copyPath(it) },
                addBookmark = { activePaneFragment().addBookmark(it) },
                createShortcut = { activePaneFragment().createShortcut(it) },
                properties = { activePaneFragment().showPropertiesDialog(it) },
                selectAll = { activePaneFragment().selectAllFiles() }
            )
        )
    }

    private var fastScrollPopup: RecyclerViewFastScrollPopup? = null

    private var isSpeedDialShown = true

    private var isSpeedDialHiddenByScroll = false

    private var isBottomPanelVisible = false

    private var speedDialHiddenTranslationY = 0f

    private val speedDialScrollInterpolator: Interpolator = FastOutSlowInInterpolator()

    private var secondaryPane: FileListFragment? = null
    private val workspaceController = FileListWorkspaceController()
    private val activePane: BrowserPane
        get() = workspaceController.activePane
    private val dualPaneVisible: Boolean
        get() = workspaceController.dualPaneVisible
    private var preserveSelectionOnOverlayFinish = false
    private val searchController = FileListSearchController(
        isHostResumed = { isResumed },
        activeViewModel = { activePaneFragment().viewModel }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FileListFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        bindWorkspace(activity)
        bindFileListViews(activity)
        restoreAndObserveViewState(activity, savedInstanceState)
    }

    private fun bindWorkspace(activity: AppCompatActivity) {
        if (!isEmbeddedPane) {
            workspaceController.bind(
                context = requireContext(),
                views = FileListWorkspaceController.Views(
                    contentLayout = binding.contentLayout,
                    primaryBreadcrumb = binding.breadcrumbLayout,
                    persistentDrawerLayout = binding.persistentDrawerLayout,
                    recyclerView = binding.recyclerView
                ),
                onDividerFractionChanged = { fraction ->
                    (activity as? FileListActivity)
                        ?.onDividerFractionChanged(this@FileListFragment, fraction)
                },
                onSecondaryPaneTouched = { activatePane(BrowserPane.SECONDARY) }
            )
            navigationFragment = childFragmentManager.findFragmentById(R.id.navigationFragment)
                as? NavigationFragment
                ?: NavigationFragment().also {
                    childFragmentManager.commit { add(R.id.navigationFragment, it) }
                }
            navigationFragment.listener = this
            activity.setTitle(R.string.file_list_title)
            activity.setSupportActionBar(binding.toolbar)
            (activity as? FileListActivity)?.bindTabStrip(this, binding.browserTabLayout)
            (activity as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuBinding = FileListFragmentMenuBinding.inflate(menu, menuInflater)
                menu.showOptionalIcons()
                setUpSearchView()
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_new_tab)?.isVisible =
                    (activity as? FileListActivity)?.areTabsEnabled == true
                menu.findItem(R.id.action_dual_pane)?.apply {
                    isVisible = (activity as? FileListActivity)?.isDualPaneAvailable == true
                    isChecked = (activity as? FileListActivity)
                        ?.isDualPaneEnabled(this@FileListFragment) == true
                }
                updateViewSortMenuItems()
                updateSelectAllMenuItem()
                updateShowHiddenFilesMenuItem()
                updateRecycleBinMenuItems()
                updatePasteMenuItem()
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean =
                when (BrowserMenuCommandPolicy.resolve(item.itemId)) {
                    BrowserMenuCommandPolicy.Command.OPEN_DRAWER -> {
                        binding.drawerLayout?.openDrawer(GravityCompat.START)
                        if (binding.persistentDrawerLayout != null) {
                            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.putValue(
                                !Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat
                            )
                        }
                        true
                    }
                    BrowserMenuCommandPolicy.Command.SORT -> {
                        activePaneFragment().showViewSortDialog()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.NEW_TASK -> {
                        activePaneFragment().newTask()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.NEW_TAB -> {
                        (activity as? FileListActivity)?.openNewTab()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.TOGGLE_DUAL_PANE -> {
                        (activity as? FileListActivity)?.toggleDualPane()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.NAVIGATE_UP -> {
                        activePaneFragment().navigateUp()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.NAVIGATE_TO -> {
                        activePaneFragment().showNavigateToPathDialog()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.REFRESH -> {
                        activePaneFragment().refresh()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.PASTE -> {
                        dispatchBrowserCommand(BrowserCommand.PASTE)
                        true
                    }
                    BrowserMenuCommandPolicy.Command.RESTORE_ALL -> {
                        activePaneFragment().restoreAllFromRecycleBin()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.DELETE_ALL -> {
                        activePaneFragment().confirmDeleteAllFromRecycleBin()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.SELECT_ALL -> {
                        activePaneFragment().selectAllFiles()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.TOGGLE_HIDDEN -> {
                        setShowHiddenFiles(!menuBinding.showHiddenFilesItem.isChecked)
                        true
                    }
                    BrowserMenuCommandPolicy.Command.SHARE -> {
                        activePaneFragment().share()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.COPY_PATH -> {
                        activePaneFragment().copyPath()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.OPEN_TERMINAL -> {
                        activePaneFragment().openInTerminalLauncher()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.ADD_BOOKMARK -> {
                        activePaneFragment().addBookmark()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.CREATE_SHORTCUT -> {
                        activePaneFragment().createShortcut()
                        true
                    }
                    BrowserMenuCommandPolicy.Command.SYNC_PANES -> {
                        val secondary = secondaryPane
                        if (ensureProAccess(ProFeature.SYNC_PROFILES) &&
                            dualPaneVisible && secondary != null
                        ) {
                            startActivity(
                                SyncProfilesActivity.createIntent(
                                    requireContext(),
                                    viewModel.currentPath.toAppPath(),
                                    secondary.viewModel.currentPath.toAppPath()
                                )
                            )
                        }
                        true
                    }
                    null -> false
                }
            }, viewLifecycleOwner, Lifecycle.State.RESUMED)
            ensureWorkspaceLayout()
        } else {
            configureEmbeddedPane()
        }
    }

    private fun bindFileListViews(activity: AppCompatActivity) {
        overlayActionMode = OverlayToolbarActionMode(binding.overlayToolbar)
        bottomPanelController.bind(
            context = requireContext(),
            actionMode = PersistentBarLayoutToolbarActionMode(
                binding.persistentBarLayout,
                binding.bottomBarLayout,
                binding.bottomToolbar
            ),
            views = FileListBottomPanelController.Views(
                bottomToolbar = binding.bottomToolbar,
                createFileNameEdit = binding.bottomCreateFileNameEdit,
                selectionActionLayout = binding.selectionActionLayout,
                primaryActions = listOf(
                    binding.selectionPrimaryAction1,
                    binding.selectionPrimaryAction2,
                    binding.selectionPrimaryAction3,
                    binding.selectionPrimaryAction4
                ),
                moreAction = binding.selectionMoreAction
            ),
            onSelectionActionClicked = ::onSelectionActionClicked,
            onNavigationClicked = ::onBottomToolbarNavigationIconClicked,
            onMenuItemClicked = ::onBottomActionModeMenuItemClicked,
            onPanelVisibilityChanged = ::setBottomPanelVisible
        )
        val contentLayoutInitialPaddingBottom = binding.contentLayout.paddingBottom
        binding.appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
            binding.contentLayout.updatePaddingRelative(
                bottom = contentLayoutInitialPaddingBottom +
                    binding.appBarLayout.totalScrollRange + verticalOffset
            )
        }
        binding.appBarLayout.syncBackgroundColorTo(binding.overlayToolbar)
        binding.breadcrumbLayout.setListener(this)
        if (!(activity.hasSw600Dp && activity.isOrientationLandscape)) {
            binding.swipeRefreshLayout.setProgressViewEndTarget(
                true, binding.swipeRefreshLayout.progressViewEndOffset
            )
        }
        binding.swipeRefreshLayout.setOnRefreshListener { this.refresh() }
        layoutManager = GridLayoutManager(activity, 1)
        binding.recyclerView.layoutManager = layoutManager
        adapter = FileListAdapter(this)
        binding.recyclerView.adapter = adapter
        operationController.bind(
            requireContext(),
            adapter,
            binding.recyclerView,
            ::refresh
        )
        externalActionController.bind(
            context = requireContext(),
            pickFiles = ::pickFiles,
            navigateTo = ::navigateTo,
            confirmReplace = ::confirmReplaceFile,
            showOpenApkDialog = { OpenApkDialogFragment.show(it, this) }
        )
        (binding.recyclerView.itemAnimator as? SimpleItemAnimator)
            ?.supportsChangeAnimations = false
        renderCoordinator.attach(binding.recyclerView, layoutManager, adapter)
        binding.recyclerView.addOnLayoutChangeListener {
                _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                updateSpanCount()
            }
        }
        binding.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    notifyPaneActivated()
                }
                return false
            }
        })
        binding.recyclerView.setOnContextClickListener { view ->
            showPaneContextMenu(view)
        }
        binding.recyclerView.setOnGenericMotionListener { view, event ->
            if (
                event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
                event.actionButton == MotionEvent.BUTTON_SECONDARY &&
                binding.recyclerView.findChildViewUnder(event.x, event.y) == null
            ) {
                showPaneContextMenu(view)
            } else {
                false
            }
        }
        binding.contentLayout.setOnDragListener { target, event ->
            (activity as? FileListActivity)?.handleInternalDrag(
                this,
                viewModel.currentPath,
                target,
                event
            ) == true
        }
        fastScrollPopup = RecyclerViewFastScrollPopup(binding.recyclerView, binding.contentLayout)
        binding.recyclerView.setOnApplyWindowInsetsListener(
            ScrollingViewOnApplyWindowInsetsListener(binding.recyclerView) { bottomInset ->
                fastScrollPopup?.setBottomInset(bottomInset)
            }
        )
        binding.speedDialView.inflate(R.menu.menu_file_list_speed_dial)
        binding.speedDialView.setOnActionSelectedListener {
            val target = activePaneFragment()
            when (it.id) {
                R.id.action_create_file -> target.showCreateFileDialog()
                R.id.action_create_directory -> target.showCreateDirectoryDialog()
                R.id.action_create_vault -> startActivitySafe(
                    AddVaultDialogActivity::class.createIntent()
                )
                R.id.action_connect_cloud_drive -> startActivitySafe(
                    EditRcloneStorageActivity::class.createIntent()
                        .putArgs(EditRcloneStorageFragment.Args())
                )
            }
            // Returning false causes the speed dial to close without animation.
            //return false
            binding.speedDialView.close()
            true
        }
        binding.speedDialView.post {
            speedDialHiddenTranslationY = computeSpeedDialHiddenTranslationY()
            if (!isSpeedDialShown) {
                binding.speedDialView.translationY = speedDialHiddenTranslationY
                binding.speedDialView.alpha = 0f
                binding.speedDialView.scaleX = 0.85f
                binding.speedDialView.scaleY = 0.85f
            }
        }
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                when {
                    dy > 0 -> hideSpeedDialForScroll()
                    dy < 0 -> showSpeedDialForScroll()
                }
                if (viewModel.searchState.isSearching &&
                    layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 30) {
                    viewModel.loadMoreSearchResults()
                }
            }
        })

    }

    private fun restoreAndObserveViewState(
        activity: AppCompatActivity,
        savedInstanceState: Bundle?
    ) {
        val viewLifecycleOwner = viewLifecycleOwner
        if (!isEmbeddedPane) {
            addOnBackPressedCallback(searchController.onBackPressedCallback)
            addOnBackPressedCallback(overlayActionMode.onBackPressedCallback)
            addOnBackPressedCallback(SpeedDialViewOnBackPressedCallback(binding.speedDialView))
            binding.drawerLayout?.let {
                addOnBackPressedCallback(DrawerLayoutOnBackPressedCallback(it))
            }
        }

        if (!viewModel.hasTrail) {
            val restored = BrowserStateRestorer.restore(
                savedState = savedInstanceState,
                arguments = requireArguments(),
                intent = args.intent,
                argumentPath = argsPath,
                stateKey = STATE_CURRENT_PATH,
                downloadsAction = ACTION_VIEW_DOWNLOADS,
                shouldOpenArchive = ::shouldOpenAsArchiveView
            )
            if (restored.unavailable) {
                showToast(R.string.file_list_location_unavailable)
            }
            viewModel.resetTo(
                restored.path ?: Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
            )
            if (restored.pickOptions != null) {
                viewModel.pickOptions = restored.pickOptions
            }
        }
        viewModel.currentPathLiveData.observe(viewLifecycleOwner) { onCurrentPathChanged(it) }
        viewModel.searchViewExpandedLiveData.observe(viewLifecycleOwner) {
            onSearchViewExpandedChanged(it)
        }
        viewModel.breadcrumbLiveData.observe(viewLifecycleOwner) {
            binding.breadcrumbLayout.setData(it)
            if (isEmbeddedPane) {
                secondaryBreadcrumbForEmbeddedPane()?.setData(it)
            }
            it.paths.getOrNull(it.selectedIndex)?.let { selectedPath ->
                (activity as? FileListActivity)?.updateTabTitle(this, selectedPath)
            }
        }
        // Grid settings must be active before the view type callback calculates a span count.
        viewModel.gridColumnOverridesLiveData.observe(viewLifecycleOwner) {
            updateSpanCount()
        }
        viewModel.viewTypeLiveData.observe(viewLifecycleOwner) { onViewTypeChanged(it) }
        // Live data only calls observeForever() on its sources when it is active, so we have to
        // make view type live data active before registering later observers that read it.
        if (binding.persistentDrawerLayout != null) {
            Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.observe(viewLifecycleOwner) {
                onPersistentDrawerOpenChanged(it)
            }
        }
        viewModel.sortOptionsLiveData.observe(viewLifecycleOwner) { onSortOptionsChanged(it) }
        viewModel.viewSortPathSpecificLiveData.observe(viewLifecycleOwner) {
            onViewSortPathSpecificChanged(it)
        }
        viewModel.pickOptionsLiveData.observe(viewLifecycleOwner) { onPickOptionsChanged(it) }
        viewModel.selectedFilesLiveData.observe(viewLifecycleOwner) { onSelectedFilesChanged(it) }
        viewModel.pasteStateLiveData.observe(viewLifecycleOwner) { onPasteStateChanged(it) }
        Settings.FILE_NAME_ELLIPSIZE.observe(viewLifecycleOwner) { onFileNameEllipsizeChanged(it) }
        viewModel.fileListLiveData.observe(viewLifecycleOwner) { onFileListChanged(it) }
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.observe(viewLifecycleOwner) {
            onShowHiddenFilesChanged(it)
        }
    }

    override fun onResume() {
        super.onResume()

        if (!isEmbeddedPane && !viewModel.isNotificationPermissionRequested) {
            ensureStorageAccess()
        }
        if (!isEmbeddedPane && !viewModel.isStorageAccessRequested) {
            ensureNotificationPermission()
        }
        SearchIndexManager.ensureScheduled()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        BrowserStateRestorer.save(outState, STATE_CURRENT_PATH, viewModel.currentPathLiveData.value)
        super.onSaveInstanceState(outState)
    }

    private fun configureEmbeddedPane() {
        binding.appBarLayout.isVisible = false
        binding.speedDialView.isVisible = false
        binding.bottomBarLayout.isVisible = false
        binding.drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.root.findViewById<View>(R.id.navigationFragment)?.isVisible = false
    }

    private fun ensureWorkspaceLayout() {
        if (!isEmbeddedPane) workspaceController.ensureLayout()
    }

    internal fun updateWorkspacePresentation(
        dualPaneVisible: Boolean,
        activePane: BrowserPane,
        dividerFraction: Float,
        verticalHingeBounds: Rect?,
        persistentDrawerAllowed: Boolean
    ) {
        if (isEmbeddedPane || view == null) return
        val workspaceChange = workspaceController.updatePresentation(
            dualPaneVisible,
            activePane,
            dividerFraction,
            verticalHingeBounds,
            persistentDrawerAllowed
        )
        if (workspaceChange.secondaryPaneRequired) {
            ensureSecondaryPane()
        } else {
            detachSecondaryPane()
        }
        activePaneFragment().viewModel.currentPathLiveData.value?.let {
            (activity as? FileListActivity)?.updateTabTitle(activePaneFragment(), it)
        }
        updateOverlayToolbar()
        updateBottomToolbar()
    }

    private fun ensureSecondaryPane() {
        if (isEmbeddedPane || childFragmentManager.isStateSaved) return
        val tag = (activity as? FileListActivity)
            ?.workspaceState(this)
            ?.secondaryPaneTag
            ?: return
        val existing = childFragmentManager.findFragmentByTag(tag) as? FileListFragment
        val pane = existing ?: FileListFragment().putArgs(
            Args(
                FileListActivity::class.createIntent().setAction(Intent.ACTION_VIEW),
                embeddedPane = true
            )
        )
        workspaceController.paneLayout?.secondaryVisible = true
        if (existing == null) {
            childFragmentManager.commitNow {
                setReorderingAllowed(true)
                add(R.id.browserSecondaryPaneContainer, pane, tag)
            }
        } else if (pane.isDetached) {
            childFragmentManager.commitNow {
                setReorderingAllowed(true)
                attach(pane)
            }
        }
        secondaryPane = pane
        workspaceController.bindSecondaryBreadcrumb(
            pane,
            pane.viewModel.breadcrumbLiveData.value
        )
    }

    private fun detachSecondaryPane() {
        val pane = secondaryPane ?: childFragmentManager.fragments
            .filterIsInstance<FileListFragment>()
            .firstOrNull { it.isEmbeddedPane }
            ?: return
        secondaryPane = pane
        if (!pane.isDetached && !childFragmentManager.isStateSaved) {
            childFragmentManager.commitNow {
                setReorderingAllowed(true)
                detach(pane)
            }
        }
    }

    private fun secondaryBreadcrumbForEmbeddedPane(): BreadcrumbLayout? =
        (parentFragment as? FileListFragment)?.workspaceController?.secondaryBreadcrumb

    private fun notifyPaneActivated() {
        if (isEmbeddedPane) {
            (parentFragment as? FileListFragment)?.activatePane(BrowserPane.SECONDARY)
        } else {
            activatePane(BrowserPane.PRIMARY)
        }
    }

    private fun activatePane(pane: BrowserPane) {
        val host = if (isEmbeddedPane) parentFragment as? FileListFragment else this
        if (host == null || !host.workspaceController.activate(pane)) return
        host.collapseSearchView(preserveSession = true)
        (activity as? FileListActivity)?.onActivePaneChanged(host, pane)
        host.syncShellToActivePane()
    }

    internal fun activateForExternalInput() {
        notifyPaneActivated()
    }

    internal fun prepareInternalDrag(file: FileItem): List<Path> {
        notifyPaneActivated()
        if (viewModel.pickOptions != null || isInRecycleBinContext()) return emptyList()
        if (file !in viewModel.selectedFiles) {
            selectOnlyFile(file)
        }
        return fileItemPathsForJob(viewModel.selectedFiles)
    }

    internal fun finishInternalDragSelection() {
        viewModel.clearSelectedFiles()
    }

    override fun startDrag(file: FileItem, anchor: View, input: BrowserDragInput): Boolean =
        (activity as? FileListActivity)?.startInternalDrag(this, anchor, file, input) == true

    override fun onFileDragEvent(file: FileItem, target: View, event: android.view.DragEvent): Boolean {
        if (!file.attributes.isDirectory) return false
        val destination = file.path.toLegacyPathOrNull() ?: return false
        return (activity as? FileListActivity)
            ?.handleInternalDrag(this, destination, target, event) == true
    }

    override fun onBreadcrumbDrag(path: Path, target: View, event: android.view.DragEvent): Boolean =
        (activity as? FileListActivity)?.handleInternalDrag(this, path, target, event) == true

    internal fun autoScrollDuringDrag(target: View, event: android.view.DragEvent) {
        workspaceController.autoScrollDuringDrag(target, event)
    }

    internal fun updateWorkspaceActivePane(pane: BrowserPane) {
        if (isEmbeddedPane) return
        workspaceController.synchronizeActivePane(pane)
        activePaneFragment().viewModel.currentPathLiveData.value?.let {
            (activity as? FileListActivity)?.updateTabTitle(activePaneFragment(), it)
        }
        syncShellToActivePane()
    }

    private fun syncShellToActivePane() {
        if (isEmbeddedPane) return
        (requireActivity() as MenuHost).invalidateMenu()
        updateOverlayToolbar()
        updateBottomToolbar()
        searchController.syncToActivePane()
    }

    private fun activePaneFragment(): FileListFragment =
        if (isEmbeddedPane) this else workspaceController.active(this, secondaryPane)

    private fun otherPaneFragment(): FileListFragment? =
        if (isEmbeddedPane) null else workspaceController.other(this, secondaryPane)

    private fun setUpSearchView() {
        searchController.bind(menuBinding.searchItem)
    }

    private fun collapseSearchView(preserveSession: Boolean = false) =
        searchController.collapse(preserveSession)

    fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val pickOptions = activePaneFragment().viewModel.pickOptions
        if (
            event.isCtrlPressed &&
            keyCode == KeyEvent.KEYCODE_A &&
            (pickOptions == null || pickOptions.allowMultiple)
        ) {
            activePaneFragment().selectAllFiles()
            return true
        }
        if (bottomPanelController.performShortcut(keyCode, event)) {
            return true
        }
        if (overlayActionMode.isActive) {
            val menu = overlayActionMode.menu
            menu.setQwertyMode(
                KeyCharacterMap.load(event.deviceId).keyboardType != KeyCharacterMap.NUMERIC
            )
            if (menu.performShortcut(keyCode, event, 0)) {
                return true
            }
        }
        return false
    }

    private fun onPersistentDrawerOpenChanged(open: Boolean) {
        binding.persistentDrawerLayout?.let {
            if (open) {
                it.openDrawer(GravityCompat.START)
            } else {
                it.closeDrawer(GravityCompat.START)
            }
        }
        updateSpanCount()
    }

    private fun onCurrentPathChanged(path: Path) {
        requireArguments().putParcelable(STATE_CURRENT_PATH, path.toAppPath())
        (activity as? FileListActivity)?.updateTabTitle(this, path)
        (requireActivity() as MenuHost).invalidateMenu()
        updateOverlayToolbar()
        updateBottomToolbar()
        if (isEmbeddedPane && (activity as? FileListActivity)?.isPaneActive(this) == true) {
            (parentFragment as? FileListFragment)?.syncShellToActivePane()
        }
    }

    private fun isInRecycleBinContext(): Boolean =
        isInRecycleBinContext(viewModel.currentPathLiveData.value)

    private fun onSearchViewExpandedChanged(expanded: Boolean) {
        searchController.setBackHandlingEnabled(expanded)
        updateViewSortMenuItems()
        if (isEmbeddedPane && (activity as? FileListActivity)?.isPaneActive(this) == true) {
            (parentFragment as? FileListFragment)
                ?.searchController
                ?.setBackHandlingEnabled(expanded)
        }
    }

    private fun onFileListChanged(stateful: Stateful<List<FileItem>>) {
        val files = stateful.value
        val isSearching = viewModel.searchState.isSearching
        val presentation = BrowserListPresentationPolicy.decide(
            phase = when (stateful) {
                is Failure -> BrowserLoadPhase.FAILURE
                is Loading -> BrowserLoadPhase.LOADING
                else -> BrowserLoadPhase.SUCCESS
            },
            counts = files?.let(FileListSubtitleCounts::from),
            isSearching = isSearching
        )
        applySubtitle(binding.toolbar, presentation.subtitle)
        if (isEmbeddedPane && (activity as? FileListActivity)?.isPaneActive(this) == true) {
            val hostToolbar = (parentFragment as? FileListFragment)?.binding?.toolbar
            hostToolbar?.let { applySubtitle(it, presentation.subtitle) }
        }
        val hasFiles = !files.isNullOrEmpty()
        binding.swipeRefreshLayout.isRefreshing = presentation.showRefresh
        binding.progress.fadeToVisibilityUnsafe(presentation.showBlockingProgress)
        binding.errorText.fadeToVisibilityUnsafe(presentation.showError)
        val throwable = (stateful as? Failure)?.throwable
        if (throwable != null) {
            when (val hostKeyException = throwable.findSftpHostKeyVerificationException()) {
                is SftpUnknownHostKeyException -> {
                    sftpHostKeyDialogController.showUnknown(hostKeyException)
                    val message = getString(R.string.file_list_sftp_host_key_unknown_error)
                    if (hasFiles) {
                        showToast(message)
                    } else {
                        binding.errorText.text = message
                    }
                }
                is SftpHostKeyMismatchException -> {
                    sftpHostKeyDialogController.showMismatch(hostKeyException)
                    val message = getString(R.string.file_list_sftp_host_key_mismatch_error)
                    if (hasFiles) {
                        showToast(message)
                    } else {
                        binding.errorText.text = message
                    }
                }
                else -> {
                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", throwable)
                    val error = throwable.toString()
                    if (hasFiles) {
                        showToast(error)
                    } else {
                        binding.errorText.text = error
                    }
                }
            }
        }
        binding.emptyView.fadeToVisibilityUnsafe(presentation.showEmpty)
        if (stateful is Success) {
            renderCoordinator.setPendingLayoutState(viewModel.pendingState)
        }
        if (files != null) {
            updateAdapterFileList()
        } else {
            clearAdapterFileList()
        }
    }

    private fun applySubtitle(toolbar: androidx.appcompat.widget.Toolbar, subtitle: BrowserSubtitle) {
        when (subtitle) {
            BrowserSubtitle.Error -> toolbar.setSubtitle(R.string.error)
            BrowserSubtitle.Loading -> toolbar.setSubtitle(R.string.loading)
            is BrowserSubtitle.Counts -> toolbar.subtitle = getCountSubtitle(subtitle)
        }
    }

    private fun getCountSubtitle(counts: BrowserSubtitle.Counts): String {
        val directoryCountText = if (counts.directories > 0) {
            getQuantityString(
                R.plurals.file_list_subtitle_directory_count_format,
                counts.directories,
                counts.directories
            )
        } else {
            null
        }
        val fileCountText = if (counts.files > 0) {
            getQuantityString(
                R.plurals.file_list_subtitle_file_count_format, counts.files, counts.files
            )
        } else {
            null
        }
        return when {
            !directoryCountText.isNullOrEmpty() && !fileCountText.isNullOrEmpty() ->
                (directoryCountText + getString(R.string.file_list_subtitle_separator)
                    + fileCountText)
            !directoryCountText.isNullOrEmpty() -> directoryCountText
            !fileCountText.isNullOrEmpty() -> fileCountText
            else -> getString(R.string.empty)
        }
    }

    private fun onViewTypeChanged(viewType: FileViewType) {
        renderCoordinator.setViewType(viewType)
        updateViewSortMenuItems()
    }

    private fun updateSpanCount() {
        renderCoordinator.requestSpanCountUpdate()
    }

    private fun availableGridWidthDp(): Int {
        var widthDp = binding.recyclerView.width
            .takeIf { it > 0 }
            ?.let { (it / resources.displayMetrics.density).roundToInt() }
            ?: resources.configuration.screenWidthDp
        val persistentDrawerLayout = binding.persistentDrawerLayout
        if (persistentDrawerLayout != null &&
            persistentDrawerLayout.isDrawerOpen(GravityCompat.START)
        ) {
            widthDp -= getDimensionDp(R.dimen.navigation_max_width).roundToInt()
        }
        return widthDp
    }

    private fun calculateSpanCount(viewType: FileViewType): Int =
        when (viewType) {
            FileViewType.LIST -> 1
            FileViewType.GRID -> GridLayoutPolicy.spanCount(
                availableGridWidthDp(),
                viewModel.gridColumnOverrides
            )
        }

    private fun onSortOptionsChanged(sortOptions: FileSortOptions) {
        renderCoordinator.setSortOptions(sortOptions)
        updateViewSortMenuItems()
    }

    private fun onViewSortPathSpecificChanged(pathSpecific: Boolean) {
        updateViewSortMenuItems()
    }

    private fun updateViewSortMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val searchViewExpanded = activePaneFragment().viewModel.isSearchViewExpanded
        menuBinding.viewSortItem.isVisible = !searchViewExpanded
        if (searchViewExpanded) {
            return
        }
    }

    private fun showViewSortDialog() {
        val activity = requireActivity() as AppCompatActivity
        val gridWidthClass = GridLayoutPolicy.widthClass(availableGridWidthDp())
        FileSortDialogController.show(
            activity,
            layoutInflater,
            FileSortDialogController.State(
                viewModel.viewType,
                viewModel.sortOptions,
                viewModel.isViewSortPathSpecific,
                viewModel.gridColumnOverrides.valueFor(gridWidthClass)
            )
        ) { selection ->
            FileViewSortSelectionApplier.apply(
                selection = selection,
                widthClass = gridWidthClass,
                setPathSpecificMode = viewModel::setViewSortPathSpecificMode,
                setViewType = viewModel::setViewType,
                setGridColumns = viewModel::setGridColumnOverride,
                setSortBy = viewModel::setSortBy,
                setSortOrder = viewModel::setSortOrder,
                setDirectoriesFirst = viewModel::setSortDirectoriesFirst
            )
        }
    }

    private fun navigateUp() {
        navigationCoordinator.navigateUp()
    }

    fun navigateUpOnBackPressed(): Boolean =
        activePaneFragment().navigationCoordinator.navigateUpOnBackPressed()

    fun canNavigateUpOnBackPressed(): Boolean =
        activePaneFragment().navigationCoordinator.canNavigateUp()

    fun isInPickFlow(): Boolean = viewModel.pickOptions != null

    private fun showNavigateToPathDialog() {
        NavigateToPathDialogFragment.show(currentPath, this)
    }

    private fun newTask() {
        openInNewTask(currentPath)
    }

    override fun onDestroyView() {
        if (!isEmbeddedPane) {
            (activity as? FileListActivity)?.unbindTabStrip(binding.browserTabLayout)
        }
        renderCoordinator.detach()
        fastScrollPopup?.detach()
        fastScrollPopup = null
        searchController.release()
        bottomPanelController.release()
        operationController.release()
        externalActionController.release()
        workspaceController.release()
        super.onDestroyView()
    }

    private fun refresh() {
        viewModel.reload()
    }

    private fun setShowHiddenFiles(showHiddenFiles: Boolean) {
        Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(showHiddenFiles)
    }

    private fun onShowHiddenFilesChanged(showHiddenFiles: Boolean) {
        updateAdapterFileList()
        updateShowHiddenFilesMenuItem()
    }

    private fun updateAdapterFileList() {
        var files = viewModel.fileListStateful.value ?: return
        if (!Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat) {
            files = files.filterNot { it.isHidden }
        }
        renderCoordinator.replaceFiles(files, viewModel.searchState.isSearching)
    }

    private fun clearAdapterFileList() {
        renderCoordinator.clearFiles()
    }

    private fun updateShowHiddenFilesMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val showHiddenFiles = Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat
        menuBinding.showHiddenFilesItem.isChecked = showHiddenFiles
    }

    private fun share() {
        externalActionController.share(currentPath, MimeType.DIRECTORY)
    }

    private fun copyPath() {
        externalActionController.copyPath(currentPath)
    }

    private fun openInTerminalLauncher() {
        externalActionController.openInTerminal(currentPath)
    }

    override fun navigateTo(path: Path) {
        navigationCoordinator.navigateTo(path)
    }

    override fun copyPath(path: Path) {
        externalActionController.copyPath(path)
    }

    override fun openInNewTask(path: Path) {
        val intent = FileListActivity.createViewIntent(path.toAppPath())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        startActivitySafe(intent)
    }

    override fun openInNewTab(path: Path) {
        (activity as? FileListActivity)?.openNewTab(path.toAppPath())
    }

    private fun onPickOptionsChanged(pickOptions: PickOptions?) {
        requireActivity().title = FileListPickerCoordinator.resolveTitle(
            requireContext(),
            args.intent,
            pickOptions
        )
        updateSelectAllMenuItem()
        updateOverlayToolbar()
        updateBottomToolbar()
        renderCoordinator.setPickOptions(pickOptions)
    }

    private fun updateSelectAllMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val pickOptions = activePaneFragment().viewModel.pickOptions
        menuBinding.selectAllItem.isVisible = pickOptions == null || pickOptions.allowMultiple
    }

    private fun pickFiles(files: FileItemSet) {
        pickPaths(files.mapTo(linkedSetOf()) { it.path })
    }

    private fun pickPaths(paths: LinkedHashSet<AppPath>) {
        val intent = FileListPickerCoordinator.createResultIntent(
            paths,
            requireNotNull(viewModel.pickOptions)
        )
        requireActivity().run {
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun onSelectedFilesChanged(files: FileItemSet) {
        if (files.isNotEmpty()) {
            hideSearchImeForSelection()
        }
        updateOverlayToolbar()
        if (!isEmbeddedPane) {
            updateBottomToolbar()
        }
        renderCoordinator.setSelectedFiles(files)
    }

    private fun hideSearchImeForSelection() {
        if (isEmbeddedPane) {
            (parentFragment as? FileListFragment)?.hideSearchImeForSelection()
            return
        }
        searchController.hideImePreservingSearch()
    }

    private fun updateOverlayToolbar() {
        if (isEmbeddedPane) {
            (parentFragment as? FileListFragment)?.onEmbeddedSelectionChanged(this)
            return
        }
        val source = activePaneFragment()
        val files = source.viewModel.selectedFiles
        if (files.isEmpty()) {
            if (overlayActionMode.isActive) {
                preserveSelectionOnOverlayFinish = true
                overlayActionMode.finish()
                preserveSelectionOnOverlayFinish = false
            }
            return
        }
        overlayActionMode.title = getString(R.string.file_list_select_title_format, files.size)
        overlayActionMode.setMenuResource(0)
        if (!overlayActionMode.isActive) {
            binding.appBarLayout.setExpanded(true)
            binding.appBarLayout.addOnOffsetChangedListener(
                AppBarLayoutExpandHackListener(binding.recyclerView)
            )
            overlayActionMode.start(object : ToolbarActionMode.Callback {
                override fun onToolbarActionModeMenuItemClicked(
                    toolbarActionMode: ToolbarActionMode,
                    item: MenuItem
                ): Boolean = false

                override fun onToolbarActionModeFinished(toolbarActionMode: ToolbarActionMode) {
                    onOverlayActionModeFinished()
                }
            })
        }
    }

    private fun configureSelectionMenu(
        menu: Menu,
        source: FileListFragment,
        files: FileItemSet
    ) {
        BrowserSelectionMenuConfigurator.configure(
            menu = menu,
            files = files,
            pickOptions = source.viewModel.pickOptions,
            currentPath = source.viewModel.currentPath,
            inRecycleBin = source.isInRecycleBinContext(),
            otherPanePath = otherPaneFragment()?.viewModel?.currentPath,
            isWritableLocation = ::isWritableBrowserLocation,
            isMutableFile = source::isMutableArchiveOrRegularFile
        )
    }

    private fun onEmbeddedSelectionChanged(pane: FileListFragment) {
        if (dualPaneVisible && activePaneFragment() === pane) {
            updateOverlayToolbar()
            updateBottomToolbar()
        }
    }

    private fun transferSelectionToOtherPane(copy: Boolean) {
        val source = activePaneFragment()
        val target = otherPaneFragment() ?: return
        val files = source.viewModel.selectedFiles
        if (files.isEmpty() || !isWritableBrowserLocation(target.viewModel.currentPath)) return
        val paths = fileItemPathsForJob(files)
        if (copy) {
            FileOperationService.copy(paths, target.viewModel.currentPath, requireContext())
        } else {
            FileOperationService.move(paths, target.viewModel.currentPath, requireContext())
        }
        source.viewModel.clearSelectedFiles()
    }

    private fun dispatchBrowserCommand(command: BrowserCommand): Boolean =
        (activity as? FileListActivity)?.executeBrowserCommand(this, command)
            ?: BrowserCommandRouter().execute(this, command)

    private fun browserCommandContext(): BrowserCommandContext {
        val target = activePaneFragment()
        val files = target.viewModel.selectedFiles
        val currentPath = target.viewModel.currentPathLiveData.value
        return BrowserCommandContext(
            selectionCount = files.size,
            isPicker = target.viewModel.pickOptions != null,
            inRecycleBin = target.isInRecycleBinContext(),
            sourceReadOnly = files.any {
                it.path.toLegacyPathOrNull()?.fileSystem?.isReadOnly == true
            },
            selectionMutable = files.all(::isMutableArchiveOrRegularFile),
            hasPasteFiles = target.viewModel.pasteState.files.isNotEmpty(),
            currentLocationWritable = currentPath?.let(::isWritableBrowserLocation) == true,
            otherPaneWritable = otherPaneFragment()
                ?.viewModel
                ?.currentPath
                ?.let(::isWritableBrowserLocation) == true
        )
    }

    override fun isBrowserCommandAvailable(command: BrowserCommand): Boolean =
        browserCommandCoordinator.isAvailable(command)

    override fun performBrowserCommand(command: BrowserCommand): Boolean =
        browserCommandCoordinator.execute(command)

    private fun onSelectionActionClicked(itemId: Int): Boolean {
        val target = activePaneFragment()
        BrowserPackageSelectionActionHandler.handle(
            itemId = itemId,
            file = target.viewModel.selectedFiles.singleOrNull(),
            context = target.requireContext(),
            ensurePackageSigningAccess = {
                target.ensureProAccess(ProFeature.PACKAGE_SIGNING)
            },
            launch = target::startActivity,
            clearSelection = target.viewModel::clearSelectedFiles
        )?.let { return it }
        val action = BrowserSelectionActionRouter.resolve(itemId) ?: return false
        return selectionCoordinator.perform(action)
    }

    override fun showContextMenu(file: FileItem, anchor: View): Boolean {
        notifyPaneActivated()
        if (file !in viewModel.selectedFiles) {
            selectOnlyFile(file)
        }
        val commandHost =
            (if (isEmbeddedPane) parentFragment as? FileListFragment else this) ?: return false
        commandHost.updateOverlayToolbar()
        val popup = PopupMenu(requireContext(), anchor).apply {
            inflate(
                if (viewModel.pickOptions != null) {
                    R.menu.menu_file_list_pick
                } else {
                    R.menu.menu_file_list_select
                }
            )
            menu.showOptionalIcons()
        }
        commandHost.configureSelectionMenu(popup.menu, this, viewModel.selectedFiles)
        if (
            ArchiveEditCapabilities.isEditableLocation(viewModel.currentPath) &&
            viewModel.pasteState.files.isNotEmpty()
        ) {
            popup.menu.add(
                Menu.NONE,
                R.id.action_paste,
                Menu.NONE,
                R.string.archive_edit_paste_here
            ).setIcon(R.drawable.ic_paste_control_normal_24dp)
        }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_paste) {
                commandHost.dispatchBrowserCommand(BrowserCommand.PASTE)
            } else {
                commandHost.onSelectionActionClicked(item.itemId)
            }
        }
        popup.show()
        return true
    }

    private fun showPaneContextMenu(anchor: View): Boolean {
        notifyPaneActivated()
        val target = activePaneFragment()
        val popup = PopupMenu(requireContext(), anchor).apply {
            inflate(R.menu.menu_file_list_context_background)
            menu.showOptionalIcons()
        }
        val isPicker = target.viewModel.pickOptions != null
        val readOnly = !isWritableBrowserLocation(target.viewModel.currentPath)
        val inEditableArchive =
            ArchiveEditCapabilities.isEditableLocation(target.viewModel.currentPath)
        val pasteAvailable = !isPicker &&
            (activity as? FileListActivity)
                ?.isBrowserCommandAvailable(this, BrowserCommand.PASTE) == true
        popup.menu.findItem(R.id.action_paste).isVisible = pasteAvailable
        popup.menu.findItem(R.id.action_create_file).isVisible =
            !isPicker && !readOnly && !inEditableArchive
        popup.menu.findItem(R.id.action_create_directory).isVisible = !isPicker && !readOnly
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_paste -> dispatchBrowserCommand(BrowserCommand.PASTE)
                R.id.action_create_file -> {
                    target.showCreateFileDialog()
                    true
                }
                R.id.action_create_directory -> {
                    target.showCreateDirectoryDialog()
                    true
                }
                R.id.action_refresh -> {
                    target.refresh()
                    true
                }
                else -> false
            }
        }
        popup.show()
        return true
    }

    private fun openBatchRename(files: FileItemSet) {
        val visibleFiles = (viewModel.fileListStateful as? Success)?.value.orEmpty()
        val selectedPaths = files.map { it.path }.toSet()
        val orderedSelection = visibleFiles.filter { it.path in selectedPaths }
            .ifEmpty { files.toList() }
        val sessionId = BatchRenameSessionStore.create(
            files = orderedSelection,
            existingNames = visibleFiles.map { it.name }
        )
        startActivity(BatchRenameActivity.createIntent(requireContext(), sessionId))
        viewModel.clearSelectedFiles()
    }

    private fun onOverlayActionModeFinished() {
        if (!preserveSelectionOnOverlayFinish) {
            activePaneFragment().viewModel.clearSelectedFiles()
        }
    }

    private fun confirmReplaceFile(file: FileItem, setFileName: Boolean = true) {
        if (setFileName) {
            val fileName = file.name
            binding.bottomCreateFileNameEdit.setText(fileName)
            binding.bottomCreateFileNameEdit.setSelection(
                0, fileName.asFileName().baseName.length
            )
        }
        ConfirmReplaceFileDialogFragment.show(file, this)
    }

    override fun replaceFile(file: FileItem) {
        pickFiles(fileItemSetOf(file))
    }

    private fun cutFiles(files: FileItemSet) {
        operationLauncher.cut(files)
    }

    private fun copyFiles(files: FileItemSet) {
        operationLauncher.copy(files)
    }

    private fun confirmDeleteFiles(files: FileItemSet) {
        operationLauncher.confirmDelete(files)
    }

    override fun deleteFiles(files: FileItemSet, options: DeleteOptions) {
        operationLauncher.delete(files, options)
    }

    private fun restoreFiles(files: FileItemSet) {
        operationLauncher.restore(files)
    }

    private fun restoreAllFromRecycleBin() {
        operationLauncher.restoreAll()
    }

    private fun confirmDeleteAllFromRecycleBin() {
        operationLauncher.confirmDeleteAll()
    }

    private fun updateRecycleBinMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val inRecycleBin = activePaneFragment().isInRecycleBinContext()
        menuBinding.menu.findItem(R.id.action_restore_all).isVisible = inRecycleBin
        menuBinding.menu.findItem(R.id.action_delete_all).isVisible = inRecycleBin
        menuBinding.menu.findItem(R.id.action_share).isVisible =
            FileItemMenuVisibilityPolicy.shouldShowShare(isDirectory = true, isInRecycleBin = inRecycleBin)
        menuBinding.menu.findItem(R.id.action_copy_path).isVisible = !inRecycleBin
        menuBinding.menu.findItem(R.id.action_open_in_terminal).isVisible =
            FileItemMenuVisibilityPolicy.shouldShowOpenInTerminalLauncher(
                isDirectory = true,
                isInRecycleBin = inRecycleBin
            )
        menuBinding.menu.findItem(R.id.action_add_bookmark).isVisible = !inRecycleBin
        menuBinding.menu.findItem(R.id.action_create_shortcut).isVisible = !inRecycleBin
        menuBinding.menu.findItem(R.id.action_sync_panes).isVisible =
            dualPaneVisible && activePaneFragment().viewModel.pickOptions == null
    }

    private fun extractFiles(files: FileItemSet) {
        operationLauncher.extract(files)
    }

    private fun showCreateArchiveDialog(files: FileItemSet) {
        CreateArchiveDialogFragment.show(files, this)
    }

    private fun showEncryptDialog(files: FileItemSet) {
        EncryptFilesDialogFragment.show(files, this)
    }

    private fun showDecryptDialog(files: FileItemSet) {
        DecryptFilesDialogFragment.show(files, this)
    }

    override fun archive(
        files: FileItemSet,
        name: String,
        format: Int,
        filter: Int,
        password: String?
    ) {
        operationController.archive(files, name, format, filter, password)
    }

    override fun encryptFiles(files: FileItemSet, password: CharArray, algorithmId: Int, kdfId: Int) {
        operationController.encrypt(files, password, algorithmId, kdfId)
    }

    override fun decryptFiles(files: FileItemSet, password: CharArray) {
        operationController.decrypt(files, password)
    }

    private fun shareFiles(files: FileItemSet) {
        externalActionController.shareSelection(files)
    }

    private fun selectAllFiles() {
        adapter.selectAllFiles()
    }

    private fun onPasteStateChanged(pasteState: PasteState) {
        if (isEmbeddedPane) {
            (parentFragment as? FileListFragment)?.updateBottomToolbar()
        } else {
            updateBottomToolbar()
        }
        (requireActivity() as MenuHost).invalidateMenu()
    }

    private fun updatePasteMenuItem() {
        if (!this::menuBinding.isInitialized || isEmbeddedPane) return
        val target = activePaneFragment()
        val currentPath = target.viewModel.currentPathLiveData.value
        val isArchivePasteDestination = currentPath?.let {
            ArchiveEditCapabilities.isEditableLocation(it)
        } == true
        val pasteItem = menuBinding.menu.findItem(R.id.action_paste) ?: return
        pasteItem.isVisible = isBrowserCommandAvailable(BrowserCommand.PASTE)
        pasteItem.setTitle(
            if (isArchivePasteDestination) {
                R.string.archive_edit_paste_here
            } else {
                R.string.paste
            }
        )
    }

    private fun updateBottomToolbar() {
        if (isEmbeddedPane) return
        val target = activePaneFragment()
        val selectedFiles = target.viewModel.selectedFiles
        val pickOptions = target.viewModel.pickOptions
        val pasteState = target.viewModel.pasteState
        val pasteContainsOnlyArchivePaths = pasteState.files.isNotEmpty() &&
            pasteState.files.all {
                it.path.toLegacyPathOrNull()?.isArchivePath == true
            }
        val directoryConfirmationLabel = if (pickOptions?.mode == PickOptions.Mode.OPEN_DIRECTORY) {
            FileListPickerCoordinator.resolveDirectoryConfirmationLabel(
                requireContext(),
                args.intent
            )
        } else {
            null
        }
        bottomPanelController.render(
            state = FileListBottomPanelController.State(
                selectionCount = selectedFiles.size,
                selectionContainsDirectory = selectedFiles.any { it.attributes.isDirectory },
                pickerMode = pickOptions?.mode,
                pickerFileName = pickOptions?.fileName,
                initializeCreateFileName =
                    pickOptions?.mode == PickOptions.Mode.CREATE_FILE &&
                        !target.viewModel.isCreateFileNameEditInitialized,
                inRecycleBin = target.isInRecycleBinContext(),
                pasteCopy = pasteState.copy,
                pasteFileCount = pasteState.files.size,
                pasteContainsOnlyArchivePaths = pasteContainsOnlyArchivePaths,
                pasteAvailable = target.isBrowserCommandAvailable(BrowserCommand.PASTE),
                directoryConfirmationLabel = directoryConfirmationLabel
            ),
            configureSelectionMenu = { menu ->
                configureSelectionMenu(menu, target, selectedFiles)
            },
            onCreateFileNameInitialized = {
                target.viewModel.isCreateFileNameEditInitialized = true
            },
            onDirectoryConfirm = { confirmOpenDirectorySelection(target) }
        )
    }

    private fun onBottomToolbarNavigationIconClicked() {
        val target = activePaneFragment()
        if (target.viewModel.pickOptions != null) {
            requireActivity().finish()
        } else {
            target.viewModel.clearPasteState()
            bottomPanelController.finish()
        }
    }

    private fun onBottomActionModeMenuItemClicked(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_create -> {
                val fileName = binding.bottomCreateFileNameEdit.text.toString()
                if (fileName.isEmpty()) {
                    showToast(R.string.file_list_create_file_name_error_empty)
                } else if (fileName.asFileNameOrNull() == null) {
                    showToast(R.string.file_list_create_file_name_error_invalid)
                } else {
                    val target = activePaneFragment()
                    val file = target.getEntryWithName(fileName)
                    if (file != null) {
                        target.confirmReplaceFile(file, false)
                    } else {
                        val path = target.viewModel.currentPath.resolve(fileName)
                        target.pickPaths(linkedSetOf(path.toAppPath()))
                    }
                }
                true
            }
            R.id.action_paste -> {
                dispatchBrowserCommand(BrowserCommand.PASTE)
                true
            }
            else -> false
        }

    private fun confirmOpenDirectorySelection(target: FileListFragment) {
        val options = target.viewModel.pickOptions
        val currentPath = target.viewModel.currentPathLiveData.value?.toAppPath()
        com.wisso.wizefiles.util.AppLog.i(
            DIRECTORY_PICKER_LOG_TAG,
            "Confirmation requested: mode=${options?.mode}, hasCurrentPath=${currentPath != null}"
        )
        val directory = FileListPickerCoordinator.resolveOpenDirectoryResult(options, currentPath)
        if (directory == null) {
            com.wisso.wizefiles.util.AppLog.w(
                DIRECTORY_PICKER_LOG_TAG,
                "Directory result unavailable"
            )
            showToast(R.string.file_list_location_unavailable)
        } else {
            com.wisso.wizefiles.util.AppLog.i(
                DIRECTORY_PICKER_LOG_TAG,
                "Returning selected directory to caller"
            )
            target.pickPaths(linkedSetOf(directory))
        }
    }

    private fun pasteFiles(targetDirectory: Path) {
        operationLauncher.paste(targetDirectory)
    }

    private fun computeSpeedDialHiddenTranslationY(): Float {
        val layoutParams = binding.speedDialView.layoutParams as? ViewGroup.MarginLayoutParams
        return (binding.speedDialView.height + (layoutParams?.bottomMargin ?: 0)).toFloat()
    }

    private fun speedDialScrollDurationMillis(): Long =
        resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

    private fun hideSpeedDialForScroll() {
        isSpeedDialHiddenByScroll = true
        updateSpeedDialVisibility()
    }

    private fun showSpeedDialForScroll() {
        isSpeedDialHiddenByScroll = false
        updateSpeedDialVisibility()
    }

    private fun setBottomPanelVisible(visible: Boolean) {
        if (isBottomPanelVisible == visible) return
        isBottomPanelVisible = visible
        updateSpeedDialVisibility()
    }

    private fun updateSpeedDialVisibility() {
        val shouldShow = !isSpeedDialHiddenByScroll && !isBottomPanelVisible
        if (isSpeedDialShown == shouldShow) return

        val speedDialView = binding.speedDialView
        if (!shouldShow && speedDialView.isOpen) {
            speedDialView.close()
        }
        val hiddenTranslationY = speedDialHiddenTranslationY.takeIf { it > 0f }
            ?: computeSpeedDialHiddenTranslationY().also { speedDialHiddenTranslationY = it }
        val animationDuration = speedDialScrollDurationMillis()
        isSpeedDialShown = shouldShow
        speedDialView.animate()
            .cancel()
        speedDialView.animate()
            .translationY(if (shouldShow) 0f else hiddenTranslationY)
            .alpha(if (shouldShow) 1f else 0f)
            .scaleX(if (shouldShow) 1f else 0.85f)
            .scaleY(if (shouldShow) 1f else 0.85f)
            .setDuration(animationDuration)
            .setInterpolator(speedDialScrollInterpolator)
            .start()
    }

    private fun onFileNameEllipsizeChanged(fileNameEllipsize: TextUtils.TruncateAt) {
        renderCoordinator.setNameEllipsize(fileNameEllipsize)
    }

    override fun clearSelectedFiles() {
        viewModel.clearSelectedFiles()
    }

    override fun selectFile(file: FileItem, selected: Boolean) {
        viewModel.selectFile(file, selected)
    }

    override fun selectFiles(files: FileItemSet, selected: Boolean) {
        viewModel.selectFiles(files, selected)
    }

    override fun openFile(file: FileItem) {
        externalActionController.open(file)
    }

    override fun installApk(file: FileItem) {
        externalActionController.installApk(file)
    }

    override fun viewApk(file: FileItem) {
        externalActionController.viewApk(file)
    }

    private fun openFileWith(file: FileItem) {
        externalActionController.openWith(file)
    }

    private fun selectOnlyFile(file: FileItem) {
        viewModel.clearSelectedFiles()
        viewModel.selectFile(file, true)
    }

    override fun hasEntryWithName(name: String): Boolean =
        externalActionController.hasEntryWithName(name)

    private fun getEntryWithName(name: String): FileItem? =
        externalActionController.entryWithName(name)

    override fun renameFile(file: FileItem, newName: String) {
        externalActionController.rename(file, newName)
    }

    private fun copyPath(file: FileItem) {
        file.path.toLegacyPathOrNull()?.let(externalActionController::copyPath)
    }

    private fun addBookmark(file: FileItem) {
        externalActionController.addBookmark(file.path)
    }

    private fun addBookmark() {
        externalActionController.addBookmark(currentPath.toAppPath())
    }

    private fun createShortcut(file: FileItem) {
        file.path.toLegacyPathOrNull()?.let {
            externalActionController.createShortcut(it, file.mimeType)
        }
    }

    private fun createShortcut() {
        externalActionController.createShortcut(currentPath, MimeType.DIRECTORY)
    }

    private fun showPropertiesDialog(file: FileItem) {
        FilePropertiesDialogFragment.show(file, this)
    }

    private fun showCreateFileDialog() {
        CreateFileDialogFragment.show(this)
    }

    override fun createFile(name: String) {
        externalActionController.create(name, directory = false)
    }

    private fun showCreateDirectoryDialog() {
        CreateDirectoryDialogFragment.show(this)
    }

    override fun createDirectory(name: String) {
        externalActionController.create(name, directory = true)
    }

    override val currentPath: Path
        get() = viewModel.currentPath

    private fun isWritableBrowserLocation(path: Path): Boolean =
        !path.fileSystem.isReadOnly || ArchiveEditCapabilities.isEditableLocation(path)

    private fun isMutableArchiveOrRegularFile(file: FileItem): Boolean {
        val path = file.path.toLegacyPathOrNull() ?: return false
        return !path.fileSystem.isReadOnly ||
            (path.isArchivePath && ArchiveEditCapabilities.isEditableLocation(path))
    }

    override fun navigateToRoot(path: Path) {
        if (!isEmbeddedPane) {
            val target = activePaneFragment()
            if (target !== this) {
                target.navigateToRoot(path)
                return
            }
        }
        collapseSearchView()
        viewModel.resetTo(path)
    }

    override fun navigateToDefaultRoot() {
        val target = if (isEmbeddedPane) this else activePaneFragment()
        target.navigateToRoot(Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat)
    }

    override fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit) {
        viewModel.currentPathLiveData.observe(owner, observer)
    }

    override fun launchNavigationAction(action: NavigationAction) {
        when (action) {
            NavigationAction.STORAGE_CLEANER ->
                startActivity(Intent(requireContext(), StorageCleanerActivity::class.java))
            NavigationAction.TRANSFER_CENTER ->
                startActivity(Intent(requireContext(), TransferCenterActivity::class.java))
            NavigationAction.SYNC_BACKUP -> {
                if (ensureProAccess(ProFeature.SYNC_PROFILES)) {
                    startActivity(SyncProfilesActivity.createIntent(requireContext()))
                }
            }
            NavigationAction.LOCAL_SHARING -> {
                if (ensureProAccess(ProFeature.BUILT_IN_SERVERS)) {
                    startActivity(
                        LocalShareActivity.createIntent(
                            requireContext(),
                            activePaneFragment().viewModel.currentPath.toAppPath()
                        )
                    )
                }
            }
            NavigationAction.NEARBY_TRANSFER ->
                startActivity(NearbyTransferActivity.createIntent(requireContext()))
        }
    }

    override fun closeNavigationDrawer() {
        binding.drawerLayout?.closeDrawer(GravityCompat.START)
    }

    companion object {
        private const val ACTION_VIEW_DOWNLOADS =
            "com.wisso.wizefiles.intent.action.VIEW_DOWNLOADS"

        private const val STATE_CURRENT_PATH = "browser_tab.current_path"


        internal fun shouldOpenAsArchiveView(
            action: String?,
            path: Path,
            mimeType: MimeType?
        ): Boolean =
            path.isArchiveFile(mimeType ?: MimeType.GENERIC) ||
                (
                    action == Intent.ACTION_VIEW &&
                        path.isContentPath &&
                        (mimeType == null || mimeType == MimeType.GENERIC)
                    )

        internal fun isInRecycleBinContext(path: Path?): Boolean =
            path?.let { RecycleBinManager.isRecycleBinPath(it) } ?: false

    }

    @Parcelize
    class Args(val intent: Intent, val embeddedPane: Boolean = false) : ParcelableArgs

}


private const val DIRECTORY_PICKER_LOG_TAG = "DirectoryPicker"
