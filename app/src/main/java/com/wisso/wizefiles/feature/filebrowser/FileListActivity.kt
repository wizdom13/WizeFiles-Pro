package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.isVisible
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import com.google.android.material.tabs.TabLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.entitlement.ProFeatureAccess
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.feature.crashreport.CrashReportPrompt
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.settings.SettingsActivity
import com.wisso.wizefiles.navigation.findNavigationRoot
import com.wisso.wizefiles.navigation.NavigationRootMapLiveData
import com.wisso.wizefiles.recyclebin.RecycleBinManager
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.extraPathList
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import java.nio.file.Path
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class FileListActivity : BaseThemedActivity() {
    private lateinit var fragment: FileListFragment
    private val commandRouter = BrowserCommandRouter()
    private val intentRouter by lazy(LazyThreadSafetyMode.NONE) { BrowserIntentRouter(this) }
    private val accessCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        BrowserAccessCoordinator(this)
    }
    private val drawerCoordinator = BrowserDrawerCoordinator(LARGE_WIDTH_DP)
    private val windowStateController = BrowserWindowStateController()
    private val dragCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        BrowserDragCoordinator(this)
    }
    private val workspaceViewModel by viewModels<BrowserWorkspaceViewModel>()
    private val tabsController: BrowserTabsController
        get() = workspaceViewModel.tabsController
    private var tabsEnabled = false
    private var boundTabLayout: TabLayout? = null
    private var isRenderingTabs = false
    private var browserSessionRegistered = false
    private var hadExistingBrowserBeforeLaunch = false
    private val tabHoverHandler = Handler(Looper.getMainLooper())
    private var pendingTabHover: Runnable? = null

    internal val areTabsEnabled: Boolean
        get() = tabsEnabled

    internal val isDualPaneAvailable: Boolean
        get() = tabsEnabled && windowStateController.widthDp >= MEDIUM_WIDTH_DP

    private val exitOnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (::fragment.isInitialized && fragment.navigateUpOnBackPressed()) {
                return
            }
            if (
                ::fragment.isInitialized &&
                BrowserNavigationFactsResolver.isExternalArchiveRoot(fragment.currentPath)
            ) {
                val returnDirectory = BrowserSessionState.returnDirectory(
                    hadExistingBrowser = hadExistingBrowserBeforeLaunch,
                    fallback = Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
                )
                hadExistingBrowserBeforeLaunch = false
                fragment.navigateToRoot(returnDirectory)
                return
            }
            if (tabsEnabled && tabsController.tabs.size > 1) {
                closeTab(tabsController.activeId ?: return)
                return
            }
            if (
                ::fragment.isInitialized &&
                shouldShowExitConfirmation(
                    isAtTopOfCurrentStorage = !fragment.canNavigateUpOnBackPressed(),
                    isPickFlow = fragment.isInPickFlow()
                )
            ) {
                showExitConfirmationDialog()
            } else {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("FileListActivity", "onCreate hasSavedState=${savedInstanceState != null}, ${AppLog.summarizeIntent(intent)}")
        val restoreIntent = intentRouter.settingsRestore(intent, savedInstanceState != null)
        if (restoreIntent != null) {
            startActivitySafe(restoreIntent)
            finish()
            return
        }

        hadExistingBrowserBeforeLaunch = BrowserSessionState.registerBrowserActivity()
        browserSessionRegistered = true
        accessCoordinator.initialize(
            onAllowed = { initializeBrowserContent(savedInstanceState) },
            onCancelled = ::finish
        )
    }


    private fun initializeBrowserContent(savedInstanceState: Bundle?) {
        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        tabsEnabled = intentRouter.supportsTabs(intent)
        if (!restoreTabs(savedInstanceState)) {
            val tab = tabsController.initialize(getString(R.string.file_list_tab_default_title))
            fragment = FileListFragment().putArgs(FileListFragment.Args(intent))
            supportFragmentManager.commitNow {
                setReorderingAllowed(true)
                add(android.R.id.content, fragment, tab.primaryPaneTag)
            }
        }
        observeWindowLayout()
        onBackPressedDispatcher.addCallback(this, exitOnBackPressedCallback)
        CrashReportPrompt.showIfPending(this)
    }

    private fun restoreTabs(savedInstanceState: Bundle?): Boolean {
        if (savedInstanceState == null) {
            return false
        }
        val savedIds = savedInstanceState.getLongArray(STATE_TAB_IDS)?.toList().orEmpty()
        val savedTitles = savedInstanceState.getStringArrayList(STATE_TAB_TITLES).orEmpty()
        val validPairs = savedIds.zip(savedTitles).filter { (id, _) ->
            val restoredFragment =
                supportFragmentManager.findFragmentByTag(BrowserTabState.fragmentTag(id))
            restoredFragment is FileListFragment
        }
        if (
            !tabsController.restore(
                ids = validPairs.map { it.first },
                titles = validPairs.map { it.second },
                requestedActiveId = savedInstanceState.getLong(STATE_ACTIVE_TAB_ID, -1L),
                requestedNextId = savedInstanceState.getLong(STATE_NEXT_TAB_ID, 0L),
                dualPaneEnabled = savedInstanceState.getBooleanArray(STATE_DUAL_PANE_ENABLED)
                    ?.toList().orEmpty(),
                activePanes = savedInstanceState.getIntArray(STATE_ACTIVE_PANES)
                    ?.map { BrowserPane.entries.getOrElse(it) { BrowserPane.PRIMARY } }.orEmpty(),
                dividerFractions = savedInstanceState.getFloatArray(STATE_DIVIDER_FRACTIONS)
                    ?.toList().orEmpty()
            )
        ) {
            return false
        }
        val activeTab = tabsController.activeTab ?: return false
        fragment = supportFragmentManager.findFragmentByTag(activeTab.primaryPaneTag)
            as FileListFragment
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            for (tab in tabsController.tabs) {
                val candidate = supportFragmentManager.findFragmentByTag(tab.primaryPaneTag)
                    ?: continue
                if (candidate === fragment) {
                    attach(candidate)
                } else {
                    detach(candidate)
                }
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        applyActiveWorkspaceLayout()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLongArray(STATE_TAB_IDS, tabsController.tabs.map { it.id }.toLongArray())
        outState.putStringArrayList(
            STATE_TAB_TITLES,
            ArrayList(tabsController.tabs.map { it.title })
        )
        outState.putLong(STATE_ACTIVE_TAB_ID, tabsController.activeId ?: -1L)
        outState.putLong(STATE_NEXT_TAB_ID, tabsController.nextTabId)
        outState.putBooleanArray(
            STATE_DUAL_PANE_ENABLED,
            tabsController.tabs.map { it.dualPaneEnabled }.toBooleanArray()
        )
        outState.putIntArray(
            STATE_ACTIVE_PANES,
            tabsController.tabs.map { it.activePane.ordinal }.toIntArray()
        )
        outState.putFloatArray(
            STATE_DIVIDER_FRACTIONS,
            tabsController.tabs.map { it.dividerFraction }.toFloatArray()
        )
        super.onSaveInstanceState(outState)
    }

    private fun observeWindowLayout() {
        findViewById<View>(android.R.id.content).addOnLayoutChangeListener {
                _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft || windowStateController.widthDp == 0) {
                if (windowStateController.updateWidth(
                        right - left,
                        resources.displayMetrics.density
                    )) {
                    applyActiveWorkspaceLayout()
                    invalidateOptionsMenu()
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@FileListActivity)
                    .windowLayoutInfo(this@FileListActivity)
                    .collect { layoutInfo ->
                        if (windowStateController.updateLayout(layoutInfo)) {
                            applyActiveWorkspaceLayout()
                        }
                    }
            }
        }
    }

    private fun applyActiveWorkspaceLayout() {
        if (!::fragment.isInitialized || !fragment.isAdded) return
        val tab = tabsController.activeTab ?: return
        if (!ProFeatureAccess.isAllowed(ProFeature.DUAL_PANE)) {
            tab.dualPaneEnabled = false
        }
        if (
            ProFeatureAccess.isAllowed(ProFeature.DUAL_PANE) &&
            windowStateController.widthDp >= EXPANDED_WIDTH_DP &&
            !tab.dualPaneEnabled &&
            getPreferences(Context.MODE_PRIVATE).getBoolean(PREF_DUAL_PANE, false)
        ) {
            tab.dualPaneEnabled = true
        }
        fragment.updateWorkspacePresentation(
            dualPaneVisible = tab.dualPaneEnabled && isDualPaneAvailable,
            activePane = tab.activePane,
            dividerFraction = tab.dividerFraction,
            verticalHingeBounds = windowStateController.verticalHingeBounds,
            persistentDrawerAllowed = drawerCoordinator.persistentDrawerAllowed(
                windowStateController.widthDp
            )
        )
    }

    internal fun toggleDualPane() {
        if (!isDualPaneAvailable) {
            Toast.makeText(this, R.string.file_list_dual_pane_requires_medium, Toast.LENGTH_SHORT)
                .show()
            return
        }
        val tab = tabsController.activeTab ?: return
        if (!tab.dualPaneEnabled && !ensureProAccess(ProFeature.DUAL_PANE)) return
        tab.dualPaneEnabled = !tab.dualPaneEnabled
        getPreferences(Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_DUAL_PANE, tab.dualPaneEnabled)
            .apply()
        applyActiveWorkspaceLayout()
        invalidateOptionsMenu()
    }

    private fun activeBrowserFragmentOrNull(): FileListFragment? =
        if (::fragment.isInitialized) fragment else null

    internal fun isDualPaneEnabled(owner: FileListFragment): Boolean {
        val activeFragment = activeBrowserFragmentOrNull() ?: return false
        return rootFragmentFor(owner) === activeFragment &&
            tabsController.activeTab?.dualPaneEnabled == true &&
            isDualPaneAvailable
    }

    internal fun isPaneActive(owner: FileListFragment): Boolean {
        val activeFragment = activeBrowserFragmentOrNull() ?: return false
        val root = rootFragmentFor(owner)
        if (root !== activeFragment) return false
        val pane = if (owner === root) BrowserPane.PRIMARY else BrowserPane.SECONDARY
        val tab = tabsController.activeTab ?: return false
        return if (tab.dualPaneEnabled && isDualPaneAvailable) {
            tab.activePane == pane
        } else {
            pane == BrowserPane.PRIMARY
        }
    }

    internal fun onActivePaneChanged(owner: FileListFragment, pane: BrowserPane) {
        val root = rootFragmentFor(owner)
        val tabId = tabIdFor(root) ?: return
        tabsController.updateWorkspace(tabId, activePane = pane)
        if (root === activeBrowserFragmentOrNull()) {
            root.updateWorkspaceActivePane(pane)
        }
    }

    internal fun onDividerFractionChanged(owner: FileListFragment, fraction: Float) {
        val tabId = tabIdFor(rootFragmentFor(owner)) ?: return
        tabsController.updateWorkspace(tabId, dividerFraction = fraction)
    }

    internal fun workspaceState(owner: FileListFragment): BrowserTabState? =
        tabIdFor(rootFragmentFor(owner))?.let { id ->
            tabsController.tabs.firstOrNull { it.id == id }
        }

    internal fun executeBrowserCommand(
        owner: FileListFragment,
        command: BrowserCommand
    ): Boolean = commandRouter.execute(owner, command)

    internal fun isBrowserCommandAvailable(
        owner: FileListFragment,
        command: BrowserCommand
    ): Boolean = commandRouter.isAvailable(owner, command)

    internal fun startInternalDrag(
        owner: FileListFragment,
        anchor: View,
        file: com.wisso.wizefiles.core.files.model.FileItem,
        input: BrowserDragInput
    ): Boolean = dragCoordinator.start(owner, anchor, owner.prepareInternalDrag(file), input)

    internal fun handleInternalDrag(
        owner: FileListFragment,
        destination: Path,
        target: View,
        event: android.view.DragEvent
    ): Boolean = dragCoordinator.handleTarget(owner, destination, target, event)

    internal fun showBrowserDragMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun rootFragmentFor(owner: FileListFragment): FileListFragment =
        owner.parentFragment as? FileListFragment ?: owner

    private fun tabIdFor(root: FileListFragment): Long? =
        tabsController.tabs.firstOrNull { tab ->
            supportFragmentManager.findFragmentByTag(tab.primaryPaneTag) === root
        }?.id

    internal fun bindTabStrip(owner: FileListFragment, tabLayout: TabLayout) {
        if (!::fragment.isInitialized || owner !== fragment) {
            return
        }
        boundTabLayout = tabLayout
        renderTabs()
    }

    internal fun unbindTabStrip(tabLayout: TabLayout) {
        if (boundTabLayout === tabLayout) {
            boundTabLayout = null
        }
    }

    internal fun updateTabTitle(owner: FileListFragment, path: Path) {
        if (!tabsEnabled) {
            return
        }
        val root = rootFragmentFor(owner)
        val tabId = tabIdFor(root) ?: return
        if (root === activeBrowserFragmentOrNull() && !isPaneActive(owner)) return
        val navigationRoot = findNavigationRoot(path, NavigationRootMapLiveData.valueCompat)
        val title = if (RecycleBinManager.isRecycleBinRootPath(path)) {
            getString(R.string.navigation_recycle_bin)
        } else {
            navigationRoot?.takeIf { it.path == path }?.getName(this)
                ?.takeIf { it.isNotBlank() }
                ?: path.name.takeIf { it.isNotBlank() }
        }
            ?: path.toString().takeIf { it.isNotBlank() }
            ?: getString(R.string.file_list_tab_default_title)
        if (tabsController.updateTitle(tabId, title)) {
            renderTabs()
        }
    }

    internal fun openNewTab(path: AppPath? = null) {
        if (!tabsEnabled || supportFragmentManager.isStateSaved) {
            return
        }
        val tab = tabsController.add(getString(R.string.file_list_tab_default_title))
        if (tab == null) {
            Toast.makeText(this, R.string.file_list_tab_limit_reached, Toast.LENGTH_SHORT).show()
            return
        }
        val previousFragment = fragment
        val tabIntent = path?.let(::createViewIntent)
            ?: FileListActivity::class.createIntent().setAction(Intent.ACTION_VIEW)
        val newFragment = FileListFragment().putArgs(FileListFragment.Args(tabIntent))
        fragment = newFragment
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            detach(previousFragment)
            add(android.R.id.content, newFragment, tab.primaryPaneTag)
        }
        renderTabs()
        applyActiveWorkspaceLayout()
    }

    private fun selectTab(id: Long) {
        if (!tabsEnabled || supportFragmentManager.isStateSaved || !tabsController.select(id)) {
            return
        }
        val newFragment = supportFragmentManager.findFragmentByTag(
            tabsController.activeTab?.primaryPaneTag ?: return
        ) as? FileListFragment ?: return
        val previousFragment = fragment
        fragment = newFragment
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            detach(previousFragment)
            attach(newFragment)
        }
        renderTabs()
        applyActiveWorkspaceLayout()
    }

    private fun closeTab(id: Long) {
        if (!tabsEnabled || supportFragmentManager.isStateSaved || tabsController.tabs.size <= 1) {
            return
        }
        val closingTab = tabsController.tabs.firstOrNull { it.id == id } ?: return
        val closingFragment = supportFragmentManager.findFragmentByTag(closingTab.primaryPaneTag)
            ?: return
        val wasActive = tabsController.activeId == id
        val newActiveId = tabsController.close(id) ?: return
        val newFragment = if (wasActive) {
            supportFragmentManager.findFragmentByTag(
                BrowserTabState.fragmentTag(newActiveId)
            ) as? FileListFragment ?: return
        } else {
            fragment
        }
        fragment = newFragment
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            remove(closingFragment)
            if (wasActive) {
                attach(newFragment)
            }
        }
        renderTabs()
        applyActiveWorkspaceLayout()
    }

    private fun renderTabs() {
        val tabLayout = boundTabLayout ?: return
        tabLayout.isVisible = tabsEnabled && tabsController.tabs.size > 1
        if (!tabLayout.isVisible) {
            tabLayout.clearOnTabSelectedListeners()
            tabLayout.removeAllTabs()
            return
        }
        isRenderingTabs = true
        tabLayout.clearOnTabSelectedListeners()
        tabLayout.removeAllTabs()
        for (tabState in tabsController.tabs) {
            val customView = layoutInflater.inflate(R.layout.item_browser_tab, tabLayout, false)
            customView.findViewById<TextView>(R.id.browserTabTitle).text = tabState.title
            customView.findViewById<ImageButton>(R.id.browserTabClose).setOnClickListener {
                closeTab(tabState.id)
            }
            customView.setOnDragListener { target, event ->
                handleTabDrag(tabState.id, target, event)
            }
            val tab = tabLayout.newTab()
                .setCustomView(customView)
                .setTag(tabState.id)
                .setContentDescription(
                    getString(R.string.file_list_tab_content_description, tabState.title)
                )
            tabLayout.addTab(tab, false)
        }
        val addTab = tabLayout.newTab()
            .setIcon(R.drawable.ic_add_control_normal_24dp)
            .setTag(ADD_TAB_TAG)
            .setContentDescription(R.string.file_list_action_new_tab)
        tabLayout.addTab(addTab, false)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (isRenderingTabs) {
                    return
                }
                when (val tag = tab.tag) {
                    is Long -> selectTab(tag)
                    ADD_TAB_TAG -> openNewTab()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) {
                if (tab.tag == ADD_TAB_TAG) {
                    openNewTab()
                }
            }
        })
        val selectedIndex = tabsController.tabs.indexOfFirst { it.id == tabsController.activeId }
        if (selectedIndex >= 0) {
            tabLayout.getTabAt(selectedIndex)?.select()
        }
        isRenderingTabs = false
    }

    private fun handleTabDrag(id: Long, target: View, event: android.view.DragEvent): Boolean {
        if (event.action == android.view.DragEvent.ACTION_DRAG_STARTED) {
            return dragCoordinator.accepts(event)
        }
        if (!dragCoordinator.accepts(event)) return false
        val progress = target.findViewById<ProgressBar>(R.id.browserTabHoverProgress)
        val close = target.findViewById<ImageButton>(R.id.browserTabClose)
        when (event.action) {
            android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                cancelPendingTabHover()
                progress.isVisible = true
                close.isEnabled = false
                pendingTabHover = Runnable {
                    if (dragCoordinator.isDragging && tabsController.activeId != id) selectTab(id)
                }.also { tabHoverHandler.postDelayed(it, TAB_HOVER_DELAY_MILLIS) }
            }
            android.view.DragEvent.ACTION_DRAG_EXITED -> {
                progress.isVisible = false
                close.isEnabled = true
                cancelPendingTabHover()
            }
            android.view.DragEvent.ACTION_DROP -> {
                progress.isVisible = false
                close.isEnabled = true
                cancelPendingTabHover()
                showBrowserDragMessage(getString(R.string.file_list_drag_drop_inside_pane))
            }
            android.view.DragEvent.ACTION_DRAG_ENDED -> {
                progress.isVisible = false
                close.isEnabled = true
                cancelPendingTabHover()
            }
        }
        return true
    }

    private fun cancelPendingTabHover() {
        pendingTabHover?.let(tabHoverHandler::removeCallbacks)
        pendingTabHover = null
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.file_list_exit_confirmation_title)
            .setMessage(R.string.file_list_exit_confirmation_message)
            .setPositiveButton(R.string.exit) { _, _ -> onExitConfirmationResult(true) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onExitConfirmationResult(false) }
            .show()
    }

    private fun onExitConfirmationResult(confirmed: Boolean) {
        if (shouldFinishAfterExitConfirmation(confirmed)) {
            finish()
        }
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val activeFragment = activeBrowserFragmentOrNull()
        if (!isEditableInputFocused()) {
            val command = BrowserKeyboardInput.shortcutCommand(keyCode, event)
            if (
                command != null &&
                activeFragment != null &&
                commandRouter.execute(activeFragment, command)
            ) {
                AppLog.d("FileListActivity", "Handled browser command $command")
                return true
            }
        }
        if (activeFragment?.onKeyShortcut(keyCode, event) == true) {
            AppLog.d("FileListActivity", "Handled key shortcut keyCode=$keyCode")
            return true
        }
        return super.onKeyShortcut(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        dragCoordinator.updateModifiers(event.isCtrlPressed, event.isShiftPressed)
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && dragCoordinator.isDragging) {
            dragCoordinator.cancel()
            return true
        }
        if (!isEditableInputFocused() && event.repeatCount == 0) {
            val command = BrowserKeyboardInput.keyUpCommand(keyCode)
            val activeFragment = activeBrowserFragmentOrNull()
            if (
                command != null &&
                activeFragment != null &&
                commandRouter.execute(activeFragment, command)
            ) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        dragCoordinator.updateModifiers(event.isCtrlPressed, event.isShiftPressed)
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        cancelPendingTabHover()
        dragCoordinator.cancel()
        if (browserSessionRegistered) {
            BrowserSessionState.unregisterBrowserActivity()
            browserSessionRegistered = false
        }
        super.onDestroy()
    }

    private fun isEditableInputFocused(): Boolean =
        BrowserKeyboardInput.isEditableInputFocused(currentFocus)

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        data += BrowserKeyboardInput.shortcutGroup(this)
    }

    companion object {
        const val EXTRA_ALLOW_PICK_DIRECTORIES = "com.wisso.wizefiles.filelist.extra.ALLOW_PICK_DIRECTORIES"
        const val EXTRA_PICK_SELECT_WITH_LONG_PRESS =
            "com.wisso.wizefiles.filelist.extra.PICK_SELECT_WITH_LONG_PRESS"
        internal const val EXTRA_OPEN_DIRECTORY_TITLE =
            "com.wisso.wizefiles.filelist.extra.OPEN_DIRECTORY_TITLE"
        internal const val EXTRA_OPEN_DIRECTORY_CONFIRMATION_LABEL =
            "com.wisso.wizefiles.filelist.extra.OPEN_DIRECTORY_CONFIRMATION_LABEL"

        internal fun shouldShowExitConfirmation(
            isAtTopOfCurrentStorage: Boolean,
            isPickFlow: Boolean
        ): Boolean = FileListLaunchRouting.shouldShowExitConfirmation(
            isAtTopOfCurrentStorage,
            isPickFlow
        )

        internal fun shouldFinishAfterExitConfirmation(confirmed: Boolean): Boolean =
            FileListLaunchRouting.shouldFinishAfterExitConfirmation(confirmed)

        internal fun supportsTabs(action: String?): Boolean =
            FileListLaunchRouting.supportsTabs(action)

        internal fun findRestoreSettingsBackupUri(
            intent: Intent,
            resolveDisplayName: (Uri) -> String? = { null }
        ): Uri? = FileListLaunchRouting.findRestoreSettingsBackupUri(
            intent,
            resolveDisplayName
        )

        internal fun createRestoreSettingsIntent(sourceIntent: Intent, backupUri: Uri): Intent =
            FileListLaunchRouting.createRestoreSettingsIntent(sourceIntent, backupUri)

        fun createViewIntent(path: AppPath): Intent =
            FileListLaunchRouting.createViewIntent(path)

        private const val STATE_TAB_IDS = "browser_tabs.ids"
        private const val STATE_TAB_TITLES = "browser_tabs.titles"
        private const val STATE_ACTIVE_TAB_ID = "browser_tabs.active_id"
        private const val STATE_NEXT_TAB_ID = "browser_tabs.next_id"
        private const val STATE_DUAL_PANE_ENABLED = "browser_tabs.dual_pane_enabled"
        private const val STATE_ACTIVE_PANES = "browser_tabs.active_panes"
        private const val STATE_DIVIDER_FRACTIONS = "browser_tabs.divider_fractions"
        private const val ADD_TAB_TAG = "browser_tabs.add"
        private const val PREF_DUAL_PANE = "dual_pane_enabled"
        private const val MEDIUM_WIDTH_DP = 600
        private const val EXPANDED_WIDTH_DP = 840
        private const val LARGE_WIDTH_DP = 1200
        private const val TAB_HOVER_DELAY_MILLIS = 600L

        internal fun normalizeOpenDirectoryInput(input: Any?): AppPath? =
            FileListLaunchRouting.normalizeOpenDirectoryInput(input)

        internal fun resolveOpenDirectoryTitle(
            customTitle: String?,
            fallbackTitle: String
        ): String = FileListLaunchRouting.resolveOpenDirectoryTitle(customTitle, fallbackTitle)

        internal fun resolveOpenDirectoryConfirmationLabel(
            customLabel: String?,
            fallbackLabel: String
        ): String = FileListLaunchRouting.resolveOpenDirectoryConfirmationLabel(
            customLabel,
            fallbackLabel
        )
    }

    data class OpenDirectoryRequest(
        val initialPath: AppPath? = null,
        val title: String? = null,
        val confirmationLabel: String? = null
    )

    class OpenFileContract : ActivityResultContract<List<MimeType>, AppPath?>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            BrowserActivityResultDispatcher.openFile(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): AppPath? =
            BrowserActivityResultDispatcher.single(resultCode, intent)
    }

    class OpenPathContract : ActivityResultContract<List<MimeType>, List<AppPath>>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            BrowserActivityResultDispatcher.openPath(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): List<AppPath> =
            BrowserActivityResultDispatcher.multiple(resultCode, intent)
    }

    class CreateFileContract :
        ActivityResultContract<Triple<MimeType, String?, AppPath?>, AppPath?>() {
        override fun createIntent(
            context: Context,
            input: Triple<MimeType, String?, AppPath?>
        ): Intent = BrowserActivityResultDispatcher.createFile(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): AppPath? =
            BrowserActivityResultDispatcher.single(resultCode, intent)
    }

    class OpenDirectoryContract : ActivityResultContract<Any?, AppPath?>() {
        override fun createIntent(context: Context, input: Any?): Intent =
            BrowserActivityResultDispatcher.openDirectory(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): AppPath? =
            BrowserActivityResultDispatcher.single(resultCode, intent)
    }
}
