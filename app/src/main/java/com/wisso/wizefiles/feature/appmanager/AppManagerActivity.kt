// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.appmanager

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.databinding.ActivityAppManagerBinding
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.extraPath
import java.nio.file.Path
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppManagerActivity : BaseThemedActivity() {
    private val viewModel by viewModels<AppManagerViewModel>()
    private lateinit var binding: ActivityAppManagerBinding
    private lateinit var adapter: AppManagerAdapter
    private var latestState = AppManagerUiState()
    private val uninstallQueue = ArrayDeque<String>()
    private var packageReceiverRegistered = false
    private var operationJob: Job? = null
    private var isOperationRunning = false
    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
        launchNextUninstall()
    }
    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
    }
    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refresh()
        }
    }
    private val backupDestinationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val destination = result.data?.extraPath?.toLegacyPathOrNull()
        if (destination == null) {
            Toast.makeText(this, R.string.app_manager_backup_destination_error, Toast.LENGTH_SHORT)
                .show()
            return@registerForActivityResult
        }
        exportSelectedApps(ExportPurpose.DurableBackup(destination))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = AppManagerAdapter(packageManager) { app ->
            if (!isOperationRunning) viewModel.toggleSelection(app.packageName)
        }
        binding.appList.adapter = adapter
        binding.chipUser.isChecked = true
        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.singleOrNull()) {
                R.id.chipAll -> AppManagerFilter.ALL
                R.id.chipSystem -> AppManagerFilter.SYSTEM
                R.id.chipDisabled -> AppManagerFilter.DISABLED
                else -> AppManagerFilter.USER
            }
            viewModel.setFilter(filter)
        }
        binding.uninstallAction.setOnClickListener { requestUninstall() }
        binding.enableDisableAction.setOnClickListener { openSelectedAppInfo() }
        binding.shareAction.setOnClickListener { exportSelectedApps(ExportPurpose.Share) }
        binding.infoAction.setOnClickListener { openSelectedAppInfo() }
        binding.openAction.setOnClickListener { openSelectedApp() }
        binding.cancelOperationButton.setOnClickListener { operationJob?.cancel() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (latestState.selectedPackageNames.isNotEmpty()) {
                    viewModel.clearSelection()
                } else {
                    finish()
                }
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) adapter.close()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (!packageReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                this,
                packageChangedReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            packageReceiverRegistered = true
        }
    }

    override fun onStop() {
        if (packageReceiverRegistered) {
            unregisterReceiver(packageChangedReceiver)
            packageReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_manager, menu)
        val searchView = menu.findItem(R.id.action_app_search).actionView as SearchView
        searchView.queryHint = getString(R.string.app_manager_search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty())
                return true
            }
        })
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_app_backup).apply {
            isVisible = latestState.selectedPackageNames.isNotEmpty()
            isEnabled = !isOperationRunning
        }
        menu.findItem(R.id.action_app_search).isEnabled = !isOperationRunning
        menu.findItem(R.id.action_app_sort).isEnabled = !isOperationRunning
        menu.findItem(R.id.action_app_sort_direction).isEnabled = !isOperationRunning
        menu.findItem(R.id.action_app_sort_direction).title = getString(
            if (latestState.sortOrder == AppManagerSortOrder.ASCENDING) {
                R.string.app_manager_sort_descending
            } else {
                R.string.app_manager_sort_ascending
            }
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        R.id.action_app_sort -> {
            showSortDialog()
            true
        }
        R.id.action_app_sort_direction -> {
            viewModel.toggleSortOrder()
            true
        }
        R.id.action_app_backup -> {
            if (latestState.selectedPackageNames.size <= 1 ||
                ensureProAccess(ProFeature.BATCH_APP_MANAGER_OPERATIONS)
            ) {
                chooseBackupDestination()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showSortDialog() {
        val sorts = AppManagerSort.entries
        val labels = arrayOf(
            getString(R.string.app_manager_sort_name),
            getString(R.string.app_manager_sort_size),
            getString(R.string.app_manager_sort_installed),
            getString(R.string.app_manager_sort_updated),
            getString(R.string.app_manager_sort_package)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_manager_sort_title)
            .setSingleChoiceItems(labels, sorts.indexOf(latestState.sort)) { dialog, which ->
                viewModel.setSort(sorts[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestUninstall() {
        val selectedApps = viewModel.selectedApps()
        if (!canUninstallSelectedApps(selectedApps)) return
        if (selectedApps.size > 1 &&
            !ensureProAccess(ProFeature.BATCH_APP_MANAGER_OPERATIONS)
        ) {
            return
        }
        if (selectedApps.size == 1) {
            startUninstallSequence(selectedApps.map(InstalledApp::packageName))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_manager_uninstall_multiple_title)
            .setMessage(getString(R.string.app_manager_uninstall_multiple_message, selectedApps.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.app_manager_uninstall) { _, _ ->
                startUninstallSequence(selectedApps.map(InstalledApp::packageName))
            }
            .show()
    }

    private fun startUninstallSequence(packageNames: List<String>) {
        if (packageNames.size > 1 &&
            !ensureProAccess(ProFeature.BATCH_APP_MANAGER_OPERATIONS)
        ) {
            return
        }
        uninstallQueue.clear()
        uninstallQueue.addAll(packageNames)
        launchNextUninstall()
    }

    private fun launchNextUninstall() {
        val packageName = uninstallQueue.pollFirst()
        if (packageName == null) {
            viewModel.clearSelection()
            viewModel.refresh()
            Toast.makeText(this, R.string.app_manager_uninstall_finished, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:$packageName")
        ).putExtra(Intent.EXTRA_RETURN_RESULT, true)
        try {
            uninstallLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_manager_uninstall_unavailable, Toast.LENGTH_SHORT)
                .show()
            launchNextUninstall()
        }
    }

    private fun openSelectedAppInfo() {
        val app = viewModel.selectedApps().singleOrNull() ?: return
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${app.packageName}")
        )
        try {
            appSettingsLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_manager_info_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSelectedApp() {
        val app = viewModel.selectedApps().singleOrNull() ?: return
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null || !app.isEnabled) {
            Toast.makeText(this, R.string.app_manager_open_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_manager_open_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseBackupDestination() {
        if (isOperationRunning || viewModel.selectedApps().isEmpty()) return
        val intent = Intent(this, FileListActivity::class.java)
            .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, false)
        backupDestinationLauncher.launch(intent)
    }

    private fun exportSelectedApps(purpose: ExportPurpose) {
        if (isOperationRunning) return
        val apps = viewModel.selectedApps()
        if (apps.isEmpty()) return
        if (apps.size > 1 &&
            !ensureProAccess(ProFeature.BATCH_APP_MANAGER_OPERATIONS)
        ) {
            return
        }
        operationJob = lifecycleScope.launch {
            setOperationRunning(true)
            try {
                val retainedForTransfer = purpose is ExportPurpose.DurableBackup
                val backups = AppBackupExporter(applicationContext).export(
                    apps,
                    retainedForTransfer
                )
                when (purpose) {
                    ExportPurpose.Share -> shareBackups(backups)
                    is ExportPurpose.DurableBackup -> {
                        FileOperationService.copy(
                            backups.map { it.file.toPath() },
                            purpose.destination,
                            this@AppManagerActivity
                        )
                        Toast.makeText(
                            this@AppManagerActivity,
                            R.string.app_manager_backup_queued,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                viewModel.clearSelection()
            } catch (exception: CancellationException) {
                if (!isFinishing) {
                    Toast.makeText(
                        this@AppManagerActivity,
                        R.string.app_manager_export_cancelled,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                throw exception
            } catch (_: Exception) {
                Toast.makeText(
                    this@AppManagerActivity,
                    R.string.app_manager_export_failed,
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setOperationRunning(false)
            }
        }
    }

    private fun shareBackups(backups: List<ExportedAppBackup>) {
        val uris = backups.map { it.file.toPath().fileProviderUri }
        val intent = Intent(
            if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        ).apply {
            type = backups.map(ExportedAppBackup::mimeType).distinct().singleOrNull()
                ?: "application/octet-stream"
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            clipData = ClipData.newUri(contentResolver, getString(R.string.app_manager_title), uris.first())
                .also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.app_manager_share_chooser)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_manager_share_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setOperationRunning(running: Boolean) {
        isOperationRunning = running
        binding.operationProgressContainer.isVisible = running
        binding.filterChips.children.forEach { it.isEnabled = !running }
        renderActionAvailability(latestState)
        invalidateOptionsMenu()
    }

    private fun render(state: AppManagerUiState) {
        latestState = state
        adapter.submitList(state.visibleApps)
        adapter.selectedPackageNames = state.selectedPackageNames
        binding.loadingProgress.isVisible = state.isLoading
        binding.appList.isVisible = !state.isLoading && state.visibleApps.isNotEmpty()
        binding.emptyText.isVisible = !state.isLoading && state.visibleApps.isEmpty()
        binding.emptyText.setText(
            if (state.loadFailed) R.string.app_manager_load_error
            else R.string.app_manager_empty
        )
        binding.summaryText.text = getString(
            R.string.app_manager_summary,
            state.visibleApps.size,
            Formatter.formatFileSize(this, state.totalBackupBytes)
        )
        val hasSelection = state.selectedPackageNames.isNotEmpty()
        binding.selectionBar.isVisible = hasSelection
        renderActionAvailability(state)
        supportActionBar?.title = if (hasSelection) {
            getString(R.string.app_manager_selected_count, state.selectedPackageNames.size)
        } else {
            getString(R.string.app_manager_title)
        }
        invalidateOptionsMenu()
    }

    private fun renderActionAvailability(state: AppManagerUiState) {
        val selectedApps = viewModel.selectedApps()
        val hasSelection = state.selectedPackageNames.isNotEmpty()
        val singleSelectedApp = selectedApps.singleOrNull()
        val enabledAction = selectedAppEnabledAction(selectedApps)

        binding.uninstallAction.isEnabled =
            canUninstallSelectedApps(selectedApps) && !isOperationRunning
        binding.enableDisableAction.apply {
            isEnabled = enabledAction != null && !isOperationRunning
            setText(
                if (enabledAction == AppEnabledAction.ENABLE) {
                    R.string.app_manager_enable
                } else {
                    R.string.app_manager_disable
                }
            )
            setIconResource(
                if (enabledAction == AppEnabledAction.ENABLE) {
                    R.drawable.ic_enable_control_normal_24dp
                } else {
                    R.drawable.ic_disable_control_normal_24dp
                }
            )
        }
        binding.shareAction.isEnabled = hasSelection && !isOperationRunning
        binding.infoAction.isEnabled = singleSelectedApp != null && !isOperationRunning
        binding.openAction.isEnabled = singleSelectedApp?.let {
            it.isEnabled && it.hasLaunchIntent && !isOperationRunning
        } == true
    }

    private sealed interface ExportPurpose {
        data object Share : ExportPurpose
        data class DurableBackup(val destination: Path) : ExportPurpose
    }
}
