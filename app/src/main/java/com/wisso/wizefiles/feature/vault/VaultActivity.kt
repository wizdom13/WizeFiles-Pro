// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ActivityVaultBinding
import com.wisso.wizefiles.databinding.IncludeFileListBottomBarBinding
import com.wisso.wizefiles.databinding.IncludeFileListSpeedDialBinding
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.FileViewType
import com.wisso.wizefiles.feature.filebrowser.GridColumnOverrides
import com.wisso.wizefiles.feature.filebrowser.GridLayoutPolicy
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.EditRcloneStorageActivity
import com.wisso.wizefiles.storage.EditRcloneStorageFragment
import com.wisso.wizefiles.storage.Storages
import com.wisso.wizefiles.storage.VaultStorage
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.ui.ScrollingViewOnApplyWindowInsetsListener
import com.wisso.wizefiles.ui.SpeedDialViewOnBackPressedCallback
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.showOptionalIcons
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.valueCompat
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class VaultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVaultBinding
    private lateinit var bottomBarBinding: IncludeFileListBottomBarBinding
    private lateinit var speedDialBinding: IncludeFileListSpeedDialBinding
    private lateinit var vaultId: String
    private lateinit var vaultName: String
    private var currentParentId: String? = null
    private val parentStack = ArrayDeque<String?>()
    private var entries: List<VaultEntry> = emptyList()
    private var directoryItemCounts: Map<String, Int> = emptyMap()
    private val selectedEntryIds = linkedSetOf<String>()
    private lateinit var adapter: VaultEntryListAdapter
    private lateinit var layoutManager: GridLayoutManager
    private lateinit var openSessionManager: VaultOpenSessionManager
    private lateinit var viewOptionsController: VaultViewOptionsController
    private lateinit var entryNameDialogController: VaultEntryNameDialogController
    private lateinit var operations: VaultActivityOperations
    private lateinit var importController: VaultImportController
    private var viewType = FileViewType.LIST
    private var gridColumnOverrides = GridColumnOverrides()
    private var sortOptions = Settings.FILE_LIST_SORT_OPTIONS.valueCompat
    private var isImporting = false

    private val importPathLauncher = registerForActivityResult(FileListActivity.OpenPathContract()) { paths ->
        if (paths.isNotEmpty()) {
            importPaths(paths.mapNotNull { it.toLegacyPathOrNull() })
        }
    }
    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { backupVault(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        bottomBarBinding = IncludeFileListBottomBarBinding.bind(binding.root)
        speedDialBinding = IncludeFileListSpeedDialBinding.bind(binding.root)
        configureSelectionUi()
        configureSpeedDial()

        vaultId = intent.getStringExtra(EXTRA_VAULT_ID) ?: run { finish(); return }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        openSessionManager = VaultOpenSessionManager(this)
        operations = VaultActivityOperations(this,vaultId)
        importController = VaultImportController(
            this,
            lifecycleScope,
            operations,
            ::setImporting,
            ::refreshList
        )
        viewOptionsController = VaultViewOptionsController(this, ::availableGridWidthDp)
        entryNameDialogController = VaultEntryNameDialogController(this)

        adapter = VaultEntryListAdapter(
            onClick = ::onEntryClicked,
            onLongClick = ::onEntryLongClicked
        )
        layoutManager = GridLayoutManager(this, 1)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnLayoutChangeListener {
                _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                updateGridSpanCount()
            }
        }
        binding.recyclerView.setOnApplyWindowInsetsListener(
            ScrollingViewOnApplyWindowInsetsListener(binding.recyclerView)
        )

        binding.lockVaultButton.setOnClickListener {
            VaultManager(this).lock(vaultId)
            finish()
        }
        binding.importButton.setOnClickListener {
            if (!isImporting) {
                importPathLauncher.launch(listOf(MimeType.ANY))
            }
        }
        binding.enableBiometricButton.setOnClickListener { enableBiometric() }

        Settings.FILE_LIST_VIEW_TYPE.observe(this) { applyViewType(it) }
        Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.observe(this) {
            gridColumnOverrides = it
            updateGridSpanCount()
        }
        Settings.FILE_LIST_SORT_OPTIONS.observe(this) {
            sortOptions = it
            submitSortedEntries()
        }

        onBackPressedDispatcher.addCallback(this) {
            handleBackNavigation()
        }
        onBackPressedDispatcher.addCallback(
            this,
            SpeedDialViewOnBackPressedCallback(speedDialBinding.speedDialView)
        )

        supportFragmentManager.setFragmentResultListener(
            UnlockVaultDialogFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            if (bundle.getString(UnlockVaultDialogFragment.KEY_VAULT_ID) == vaultId) {
                refreshList()
            }
        }
        renderImportButton()
        refreshTitle()
        if (!VaultSessionManager.isUnlocked(vaultId)) {
            UnlockVaultDialogFragment.newInstance(vaultId).show(
                supportFragmentManager,
                UnlockVaultDialogFragment::class.java.name
            )
        } else {
            refreshList()
        }
    }

    private fun applySystemBarInsets() {
        val container = binding.topToolbarContainer
        val initialPaddingLeft = container.paddingLeft
        val initialPaddingTop = container.paddingTop
        val initialPaddingRight = container.paddingRight
        val initialHeight = container.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = initialPaddingLeft + bars.left,
                top = initialPaddingTop + bars.top,
                right = initialPaddingRight + bars.right
            )
            val targetHeight = initialHeight + bars.top
            if (view.layoutParams.height != targetHeight) {
                view.layoutParams = view.layoutParams.apply { height = targetHeight }
            }
            insets
        }
        ViewCompat.requestApplyInsets(container)
    }

    private fun availableGridWidthDp(): Int =
        binding.recyclerView.width
            .takeIf { it > 0 }
            ?.let { (it / resources.displayMetrics.density).roundToInt() }
            ?: resources.configuration.screenWidthDp

    private fun applyViewType(value: FileViewType) {
        viewType = value
        adapter.viewType = value
        updateGridSpanCount()
    }

    private fun updateGridSpanCount() {
        if (!this::layoutManager.isInitialized) {
            return
        }
        layoutManager.spanCount = when (viewType) {
            FileViewType.LIST -> 1
            FileViewType.GRID -> GridLayoutPolicy.spanCount(
                availableGridWidthDp(),
                gridColumnOverrides
            )
        }
    }

    private fun showViewDialog() {
        viewOptionsController.show(sortOptions)
    }

    override fun onResume() {
        super.onResume()
        refreshTitle()
        finalizeOpenSessions()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_vault, menu)
        menu.showOptionalIcons()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> onSupportNavigateUp()
        R.id.action_view_sort -> {
            showViewDialog()
            true
        }
        R.id.action_backup_vault -> {
            createBackupLauncher.launch("$vaultId.wvault.zip")
            true
        }
        R.id.action_delete_vault -> {
            confirmDeleteVault()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        handleBackNavigation()
        return true
    }

    private fun handleBackNavigation() {
        if (clearSelection()) {
            return
        }
        when (resolveVaultBackAction(parentStack.isNotEmpty())) {
            VaultBackAction.NAVIGATE_TO_PARENT -> {
                currentParentId = parentStack.removeLast()
                refreshList()
            }
            VaultBackAction.SHOW_LEAVE_DIALOG -> showLeaveVaultDialog()
        }
    }

    private fun showLeaveVaultDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_leave_title)
            .setMessage(R.string.vault_leave_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.vault_leave_unlocked) { _, _ -> finish() }
            .setPositiveButton(R.string.vault_lock_and_leave) { _, _ ->
                VaultManager(this).lock(vaultId)
                finish()
            }
            .show()
    }

    private fun refreshTitle() {
        val metadata = getVaultMetadata()
        vaultName = metadata?.name ?: getString(R.string.vault_default_name)
        supportActionBar?.title = getString(R.string.vault_title_format, vaultName)
        updateBiometricButtonVisibility(metadata?.biometricEnabled == true)
    }

    private fun getVaultMetadata(): VaultMetadata? =
        VaultManager(this).listVaults().firstOrNull { it.vaultId == vaultId }

    private fun updateEntrySubtitle() {
        val directoryCount = entries.count { it.isDirectory }
        val fileCount = entries.size - directoryCount
        val directoryCountText = if (directoryCount > 0) {
            resources.getQuantityString(
                R.plurals.file_list_subtitle_directory_count_format,
                directoryCount,
                directoryCount
            )
        } else {
            null
        }
        val fileCountText = if (fileCount > 0) {
            resources.getQuantityString(
                R.plurals.file_list_subtitle_file_count_format,
                fileCount,
                fileCount
            )
        } else {
            null
        }
        binding.toolbar.subtitle = when {
            !directoryCountText.isNullOrEmpty() && !fileCountText.isNullOrEmpty() ->
                directoryCountText + getString(R.string.file_list_subtitle_separator) +
                    fileCountText
            !directoryCountText.isNullOrEmpty() -> directoryCountText
            !fileCountText.isNullOrEmpty() -> fileCountText
            else -> getString(R.string.empty)
        }
    }

    private fun updateBiometricButtonVisibility(isEnabled: Boolean) {
        binding.enableBiometricButton.isVisible = !isEnabled
    }

    private fun submitSortedEntries() {
        if (this::adapter.isInitialized) {
            adapter.directoryItemCounts = directoryItemCounts
            adapter.submitList(sortVaultEntries(entries, sortOptions))
        }
    }

    private fun refreshList() {
        if (!VaultSessionManager.isUnlocked(vaultId)) {
            entries = emptyList()
            directoryItemCounts = emptyMap()
            selectedEntryIds.clear()
            binding.emptyView.isVisible = true
            adapter.submitList(emptyList())
            binding.toolbar.subtitle = null
            renderSelectionState()
            return
        }
        binding.toolbar.setSubtitle(R.string.loading)
        lifecycleScope.launch {
            val listing = withContext(Dispatchers.IO) {
                VaultManager(this@VaultActivity)
                    .listEntriesWithDirectoryCounts(vaultId, currentParentId)
            }
            entries = listing.entries
            directoryItemCounts = listing.directoryItemCounts
            selectedEntryIds.retainAll(entries.mapTo(mutableSetOf()) { it.id })
            binding.emptyView.isVisible = entries.isEmpty()
            submitSortedEntries()
            updateEntrySubtitle()
            renderSelectionState()
        }
    }

    private fun configureSpeedDial() {
        speedDialBinding.speedDialView.inflate(R.menu.menu_file_list_speed_dial)
        speedDialBinding.speedDialView.setOnActionSelectedListener { action ->
            when (action.id) {
                R.id.action_create_file -> showCreateFileDialog()
                R.id.action_create_directory -> showCreateFolderDialog()
                R.id.action_create_vault -> startActivitySafe(
                    AddVaultDialogActivity::class.createIntent()
                )
                R.id.action_connect_cloud_drive -> startActivitySafe(
                    EditRcloneStorageActivity::class.createIntent()
                        .putArgs(EditRcloneStorageFragment.Args())
                )
            }
            speedDialBinding.speedDialView.close()
            true
        }
    }

    private fun configureSelectionUi() {
        bottomBarBinding.bottomToolbar.isVisible = false
        bottomBarBinding.selectionActionLayout.isVisible = true
        bottomBarBinding.bottomBarLayout.isVisible = false
        bottomBarBinding.selectionPrimaryAction1.apply {
            setText(R.string.delete)
            setIconResource(R.drawable.ic_delete_control_normal_24dp)
            setOnClickListener { confirmDeleteSelection() }
        }
        bottomBarBinding.selectionPrimaryAction2.apply {
            setText(R.string.rename)
            setIconResource(R.drawable.ic_edit_control_normal_24dp)
            setOnClickListener {
                selectedEntries().singleOrNull()?.let(::showRenameDialog)
            }
        }
        bottomBarBinding.selectionPrimaryAction3.isVisible = false
        bottomBarBinding.selectionPrimaryAction4.isVisible = false
        bottomBarBinding.selectionMoreAction.setOnClickListener { showSelectionMoreMenu() }
        binding.overlayToolbar.setNavigationOnClickListener { clearSelection() }
    }

    private fun onEntryClicked(entry: VaultEntry) {
        if (selectedEntryIds.isNotEmpty()) {
            toggleSelection(entry)
        } else if (entry.isDirectory) {
            parentStack.addLast(currentParentId)
            currentParentId = entry.id
            refreshList()
        } else {
            openEntry(entry)
        }
    }

    private fun onEntryLongClicked(entry: VaultEntry) {
        selectedEntryIds.add(entry.id)
        renderSelectionState()
    }

    private fun toggleSelection(entry: VaultEntry) {
        if (!selectedEntryIds.add(entry.id)) {
            selectedEntryIds.remove(entry.id)
        }
        renderSelectionState()
    }

    private fun selectedEntries(): List<VaultEntry> =
        entries.filter { it.id in selectedEntryIds }

    private fun clearSelection(): Boolean {
        if (selectedEntryIds.isEmpty()) {
            return false
        }
        selectedEntryIds.clear()
        renderSelectionState()
        return true
    }

    private fun renderSelectionState() {
        val selectedCount = selectedEntryIds.size
        val hasSelection = selectedCount > 0
        adapter.selectedEntryIds = selectedEntryIds
        binding.toolbar.isVisible = !hasSelection
        binding.overlayToolbar.isVisible = hasSelection
        bottomBarBinding.bottomBarLayout.isVisible = hasSelection
        speedDialBinding.speedDialView.isVisible = !hasSelection
        if (hasSelection && speedDialBinding.speedDialView.isOpen) {
            speedDialBinding.speedDialView.close()
        }
        if (!hasSelection) {
            return
        }
        binding.overlayToolbar.title = getString(
            R.string.file_list_select_title_format,
            selectedCount
        )
        bottomBarBinding.selectionPrimaryAction1.isVisible = true
        bottomBarBinding.selectionPrimaryAction2.isVisible = selectedCount == 1
        bottomBarBinding.selectionMoreAction.isVisible = true
        bottomBarBinding.selectionMoreAction.isEnabled = selectedCount < entries.size
    }

    private fun showSelectionMoreMenu() {
        androidx.appcompat.widget.PopupMenu(
            this,
            bottomBarBinding.selectionMoreAction
        ).apply {
            menu.add(
                Menu.NONE,
                R.id.action_select_all,
                Menu.NONE,
                R.string.select_all
            ).setIcon(R.drawable.ic_check_control_normal_24dp)
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_select_all) {
                    selectedEntryIds.addAll(entries.map(VaultEntry::id))
                    renderSelectionState()
                    true
                } else {
                    false
                }
            }
            menu.showOptionalIcons()
            show()
        }
    }

    private fun confirmDeleteSelection() {
        val selectedEntries = selectedEntries()
        if (selectedEntries.isEmpty()) {
            return
        }
        val message = if (selectedEntries.size == 1) {
            val entry = selectedEntries.single()
            getString(
                if (entry.isDirectory) {
                    R.string.file_delete_message_directory_format
                } else {
                    R.string.file_delete_message_file_format
                },
                entry.name
            )
        } else {
            resources.getQuantityString(
                R.plurals.file_delete_message_multiple_mixed_format,
                selectedEntries.size,
                selectedEntries.size
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteEntries(selectedEntries)
            }
            .show()
    }

    private fun deleteEntries(selectedEntries: List<VaultEntry>) {
        lifecycleScope.launch {
            val result = operations.deleteEntries(selectedEntries)
            if (result.isSuccess) {
                clearSelection()
                refreshList()
            } else {
                Toast.makeText(
                    this@VaultActivity,
                    R.string.file_job_delete_error_title,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showRenameDialog(entry: VaultEntry) {
        showEntryNameDialog(
            titleRes = R.string.rename,
            initialName = entry.name,
            excludedEntryId = entry.id,
            operation = { value ->
                VaultManager(this@VaultActivity).renameEntry(vaultId, entry.id, value)
            },
            onSuccess = {
                clearSelection()
                refreshList()
            }
        )
    }

    private fun showCreateFileDialog() {
        showEntryNameDialog(
            titleRes = R.string.file_list_action_create_file,
            operation = { value ->
                VaultManager(this@VaultActivity).importFile(
                    vaultId,
                    currentParentId,
                    value,
                    byteArrayOf()
                )
            },
            onSuccess = ::refreshList
        )
    }

    private fun showCreateFolderDialog() {
        showEntryNameDialog(
            titleRes = R.string.file_list_action_create_directory,
            operation = { value ->
                VaultManager(this@VaultActivity).createFolder(vaultId, currentParentId, value)
            },
            onSuccess = ::refreshList
        )
    }

    private fun showEntryNameDialog(
        @StringRes titleRes: Int,
        initialName: String = "",
        excludedEntryId: String? = null,
        operation: (String) -> Result<Unit>,
        onSuccess: () -> Unit
    ) {
        entryNameDialogController.show(
            titleRes = titleRes,
            initialName = initialName,
            excludedEntryId = excludedEntryId,
            entries = entries,
            currentParentId = currentParentId,
            operation = operation,
            onSuccess = onSuccess
        )
    }

    private fun importPaths(paths:List<Path>) {
        if(!isImporting) importController.importPaths(currentParentId,paths)
    }

    private fun openEntry(entry: VaultEntry) {
        lifecycleScope.launch {
            val openIntent = withContext(Dispatchers.IO) {
                openSessionManager.prepareOpenIntent(vaultId, entry)
            }
            openIntent.onSuccess { intent ->
                try {
                    startActivity(intent)
                } catch (exception: ActivityNotFoundException) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        openSessionManager.discardSession(vaultId, entry.id)
                    }
                    Toast.makeText(this@VaultActivity, R.string.vault_open_no_app, Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(this@VaultActivity, R.string.vault_open_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setImporting(importing: Boolean) {
        isImporting = importing
        renderImportButton()
    }

    private fun renderImportButton() {
        val uiState = VaultImportButtonUi.from(isImporting)
        binding.importButton.isEnabled = uiState.enabled
        binding.importButton.setText(uiState.textRes)
    }

    private fun finalizeOpenSessions() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                openSessionManager.finalizeSessionsForVault(vaultId)
            }
            if (result.failed > 0) {
                Toast.makeText(
                    this@VaultActivity,
                    getString(R.string.vault_open_reencrypt_failed, result.failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun backupVault(uri: Uri) {
        lifecycleScope.launch {
            val result = operations.exportEncryptedBackup(uri)
            Toast.makeText(
                this@VaultActivity,
                if (result.isSuccess) R.string.vault_backup_success else R.string.vault_backup_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun enableBiometric() {
        val metadata = VaultManager(this).listVaults().firstOrNull { it.vaultId == vaultId }
            ?: return
        val vmk = VaultSessionManager.getUnlockedKey(vaultId)
            ?: run {
                Toast.makeText(this, R.string.vault_error_unlock_failed, Toast.LENGTH_SHORT).show()
                return
            }
        val cipher = runCatching { VaultKeystore.encryptCipher(metadata.biometricKeyAlias) }.getOrElse {
            Toast.makeText(this, R.string.vault_error_biometric_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, R.string.vault_error_biometric_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val prompt = BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val crypto = result.cryptoObject?.cipher ?: return
                lifecycleScope.launch {
                    val saveResult = withContext(Dispatchers.IO) {
                        val wrapped = crypto.doFinal(vmk)
                        VaultManager(this@VaultActivity).saveBiometricWrappedVmk(vaultId, wrapped, crypto.iv)
                    }
                    Toast.makeText(
                        this@VaultActivity,
                        if (saveResult.isSuccess) R.string.vault_biometric_enabled else R.string.vault_biometric_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    if (saveResult.isSuccess) {
                        updateBiometricButtonVisibility(true)
                    }
                }
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.vault_biometric_enable_title, vaultName))
                .setSubtitle(getString(R.string.vault_biometric_enable_subtitle))
                .setNegativeButtonText(getString(android.R.string.cancel))
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private fun confirmDeleteVault() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_delete_title)
            .setMessage(getString(R.string.vault_delete_message, vaultName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vault_delete_confirm) { _, _ -> deleteVault() }
            .show()
    }

    private fun deleteVault() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { VaultManager(this@VaultActivity).deleteVault(vaultId) }
            if (result.isSuccess) {
                Storages.remove(VaultStorage(vaultId, vaultName, true))
                Toast.makeText(this@VaultActivity, R.string.vault_delete_success, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@VaultActivity, R.string.vault_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val EXTRA_VAULT_ID = "vault_id"

        fun createIntent(vaultId: String) = VaultActivity::class.createIntent()
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_VAULT_ID, vaultId)
    }
}
