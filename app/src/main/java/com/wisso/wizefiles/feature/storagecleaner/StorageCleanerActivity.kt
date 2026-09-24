// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.clipboardManager
import com.wisso.wizefiles.databinding.ActivityStorageCleanerBinding
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.core.files.mime.iconRes
import com.wisso.wizefiles.core.files.mime.asMimeType
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.core.files.mime.isImage
import com.wisso.wizefiles.core.files.mime.isMedia
import com.wisso.wizefiles.core.files.mime.isPdf
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.filebrowser.OpenFileActivity
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.filebrowser.isRemotePath
import com.wisso.wizefiles.provider.common.AndroidFileTypeDetector
import com.wisso.wizefiles.provider.ftp.isFtpPath
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.settings.Settings as AppSettings
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.ui.FileIconShapeView
import com.wisso.wizefiles.util.copyText
import com.wisso.wizefiles.util.isGetPackageArchiveInfoCompatible
import com.wisso.wizefiles.util.isMediaMetadataRetrieverCompatible
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.valueCompat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

private const val MENU_IGNORED_ITEMS = 1001

class StorageCleanerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStorageCleanerBinding
    private val viewModel by viewModels<StorageCleanerViewModel>()
    private var usageAccessGranted: Boolean = false
    private val fileOperationRefreshListener: () -> Unit = {
        viewModel.refreshScan()
    }
    private val adapter = StorageCleanerRecommendationsAdapter(
        onChecked = { id, checked -> viewModel.toggleSelection(id, checked) },
        onSectionSelectionToggle = { ids, selected -> viewModel.setSectionSelection(ids, selected) },
        onOpenUnusedApp = { packageName -> openAppInfo(packageName) },
        onUninstallUnusedApp = { packageName -> uninstallUnusedApp(packageName) },
        onItemClick = { recommendation -> showRecommendationDetails(recommendation) },
        onIgnore = ::ignoreRecommendation
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorageCleanerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.storage_cleaner_title)

        binding.recommendationsRecycler.layoutManager = LinearLayoutManager(this)
        binding.recommendationsRecycler.adapter = adapter
        binding.recommendationsRecycler.isNestedScrollingEnabled = false
        binding.scanButton.setOnClickListener {
            if (!viewModel.uiState.value.isScanning) {
                viewModel.refreshScan()
            }
        }
        binding.reviewDeleteButton.setOnClickListener { showDeletePreview() }
        binding.openUsageAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.visualDiskMapCard.setOnClickListener {
            startActivity(Intent(this, StorageDiskMapActivity::class.java))
        }

        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.progressBar.isIndeterminate = state.isScanning
                val categories = state.analysis?.compositionCategories.orEmpty()
                binding.visualDiskMapCard.isVisible = categories.any { it.bytes > 0L }
                val summaryRows = buildCategorySummaryRows(categories) { bytes ->
                    Formatter.formatFileSize(this@StorageCleanerActivity, bytes)
                }
                renderCategorySummaryRows(summaryRows)
                renderCategoryCompositionBar(categories, state.analysis?.totalStorageBytes ?: 0L)
                binding.lastUpdatedText.text = formatLastUpdated(state.lastUpdatedMillis)
                binding.scanButton.isEnabled =
                    !state.isScanning && !state.isPreparingDeletion
                binding.reviewDeleteButton.isEnabled =
                    state.selectedIds.isNotEmpty() &&
                        !state.isScanning &&
                        !state.isPreparingDeletion
                val recommendations = state.analysis?.recommendations
                    .orEmpty()
                    .filterNot { it.id in state.ignoredIds }
                invalidateOptionsMenu()
                adapter.submit(
                    recommendations = recommendations,
                    selectedIds = state.selectedIds,
                    formatSize = { bytes -> Formatter.formatFileSize(this@StorageCleanerActivity, bytes) },
                    colorForRecommendation = { recommendation ->
                        ContextCompat.getColor(this@StorageCleanerActivity, recommendationDotColorRes(recommendation))
                    },
                    colorForSection = { sectionKey ->
                        ContextCompat.getColor(this@StorageCleanerActivity, cleanupSectionColorRes(sectionKey))
                    },
                    sectionSummary = { sectionRecommendations ->
                        buildSectionSummary(resources, sectionRecommendations) { bytes -> Formatter.formatFileSize(this@StorageCleanerActivity, bytes) }
                    }
                )
                renderPermissionAndHintState(state, usageAccessGranted)
                state.message
                    ?.takeIf {
                        shouldShowStorageCleanerToast(
                            it,
                            getString(R.string.storage_cleaner_limited_results_prefix)
                        )
                    }
                    ?.let {
                        Toast.makeText(
                            this@StorageCleanerActivity,
                            it,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
        usageAccessGranted = UsageAccessPermissionHelper.isUsageAccessGranted(this)
        renderPermissionAndHintState(viewModel.uiState.value, usageAccessGranted)
        viewModel.onScreenOpened()
    }

    override fun onResume() {
        super.onResume()
        val latestPermissionState = UsageAccessPermissionHelper.isUsageAccessGranted(this)
        val permissionChanged = latestPermissionState != usageAccessGranted
        usageAccessGranted = latestPermissionState
        renderPermissionAndHintState(viewModel.uiState.value, usageAccessGranted)
        if (permissionChanged) {
            viewModel.onUsageAccessPermissionChanged(usageAccessGranted)
        }
    }

    override fun onStart() {
        super.onStart()
        FileOperationService.addFileListRefreshListener(fileOperationRefreshListener)
    }

    override fun onStop() {
        FileOperationService.removeFileListRefreshListener(fileOperationRefreshListener)
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(
            Menu.NONE,
            MENU_IGNORED_ITEMS,
            Menu.NONE,
            R.string.storage_cleaner_ignored_items
        ).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val ignoredCount = viewModel.uiState.value.ignoredItems.size
        menu.findItem(MENU_IGNORED_ITEMS)?.title = resources.getQuantityString(
            R.plurals.storage_cleaner_ignored_items_count,
            ignoredCount,
            ignoredCount
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == MENU_IGNORED_ITEMS) {
            showIgnoredItems()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun ignoreRecommendation(recommendation: CleanupRecommendation) {
        val ignored = viewModel.ignoreRecommendation(recommendation.id) ?: return
        Snackbar.make(
            binding.root,
            getString(R.string.storage_cleaner_item_ignored, ignored.title),
            Snackbar.LENGTH_LONG
        ).setAction(R.string.undo) {
            viewModel.restoreIgnored(ignored.id)
        }.show()
    }

    private fun showIgnoredItems() {
        val ignoredItems = viewModel.uiState.value.ignoredItems
        val content = layoutInflater.inflate(
            R.layout.dialog_storage_cleaner_ignored_items,
            null
        )
        val emptyText = content.findViewById<TextView>(R.id.ignoredItemsEmptyText)
        val container = content.findViewById<LinearLayout>(R.id.ignoredItemsContainer)
        emptyText.isVisible = ignoredItems.isEmpty()

        lateinit var dialog: AlertDialog
        ignoredItems.forEach { ignored ->
            val row = layoutInflater.inflate(
                R.layout.item_storage_cleaner_ignored_item,
                container,
                false
            )
            row.findViewById<TextView>(R.id.ignoredItemTitle).text = ignored.title
            row.findViewById<TextView>(R.id.ignoredItemMetadata).text =
                listOfNotNull(
                    ignored.type.localizedLabel(this),
                    ignored.location
                ).joinToString(" • ")
            row.findViewById<TextView>(R.id.ignoredItemRestore).setOnClickListener {
                viewModel.restoreIgnored(ignored.id)
                dialog.dismiss()
                showIgnoredItems()
            }
            container.addView(row)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.storage_cleaner_ignored_items)
            .setView(content)
            .setNegativeButton(R.string.close, null)
        if (ignoredItems.isNotEmpty()) {
            builder.setPositiveButton(R.string.storage_cleaner_restore_all) { _, _ ->
                viewModel.restoreAllIgnored()
            }
        }
        dialog = builder.show()
    }

    private fun RecommendationType.localizedLabel(context: android.content.Context): String =
        when (this) {
            RecommendationType.DUPLICATE_MEDIA, RecommendationType.DUPLICATE ->
                context.getString(R.string.storage_cleaner_duplicate_media_title)
            RecommendationType.DUPLICATE_FILES ->
                context.getString(R.string.storage_cleaner_duplicate_files_title)
            RecommendationType.LARGE_FILE ->
                context.getString(R.string.storage_cleaner_large_files_title)
            RecommendationType.APK_FILE ->
                context.getString(R.string.storage_cleaner_apk_files_title)
            RecommendationType.UNUSED_APP ->
                context.getString(R.string.storage_cleaner_unused_apps_title)
            RecommendationType.DOWNLOADS, RecommendationType.STALE_FILE ->
                context.getString(R.string.storage_cleaner_old_downloads_title)
            RecommendationType.JUNK ->
                context.getString(R.string.storage_cleaner_junk_files_title)
        }

    private fun applySystemBarInsets() {
        val root = binding.root
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft + systemBars.left,
                top = initialTop + systemBars.top,
                right = initialRight + systemBars.right,
                bottom = initialBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun formatLastUpdated(lastUpdatedMillis: Long?): String {
        if (lastUpdatedMillis == null) {
            return getString(R.string.storage_cleaner_last_updated_never)
        }
        val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(lastUpdatedMillis))
        return getString(R.string.storage_cleaner_last_updated, formatted)
    }

    private fun renderCategoryCompositionBar(categories: List<StorageCompositionSummary>, totalStorageBytes: Long) {
        val segments = buildCompositionSegments(categories, totalStorageBytes).map { segment ->
            StorageCategoryCompositionBarView.CompositionSegment(
                color = ContextCompat.getColor(this, segment.colorRes),
                fraction = segment.fraction
            )
        }
        binding.categoryCompositionBar.isVisible = segments.isNotEmpty()
        binding.categoryCompositionBar.setSegments(segments)
    }

    private fun renderCategorySummaryRows(rows: List<CategorySummaryRow>) {
        val container = binding.categorySummaryContainer
        container.removeAllViews()
        container.isVisible = rows.isNotEmpty()
        val inflater = LayoutInflater.from(this)
        rows.forEach { row ->
            val rowView = inflater.inflate(R.layout.item_storage_cleaner_category_summary, container, false)
            val dotView = rowView.findViewById<View>(R.id.categoryDot)
            val textView = rowView.findViewById<TextView>(R.id.categorySummaryRowText)
            dotView.background?.mutate()?.setTint(ContextCompat.getColor(this, row.colorRes))
            textView.text = row.text
            container.addView(rowView)
        }
    }

    private fun openAppInfo(packageName: String) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun uninstallUnusedApp(packageName: String) {
        startActivity(Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun showRecommendationDetails(recommendation: CleanupRecommendation) {
        val details = StorageCleanerItemDetailsMapper.map(this, recommendation)
        val duplicateGroup = recommendation.duplicateGroup
        var pendingKeepPath = duplicateGroup?.keepCandidatePath
        val contentView = layoutInflater.inflate(R.layout.dialog_storage_cleaner_item_details, null)
        val summaryText = contentView.findViewById<TextView>(R.id.detailsSummaryText)
        val reasonText = contentView.findViewById<TextView>(R.id.detailsReasonText)
        val impactText = contentView.findViewById<TextView>(R.id.detailsImpactText)
        val safetyHintText = contentView.findViewById<TextView>(R.id.detailsSafetyHintText)
        val fieldsSectionTitle = contentView.findViewById<TextView>(R.id.detailsFieldsSectionTitle)
        val fieldsCard = contentView.findViewById<View>(R.id.detailsFieldsCard)
        val fieldsContainer = contentView.findViewById<LinearLayout>(R.id.detailsFieldsContainer)
        val duplicateSectionTitle = contentView.findViewById<TextView>(R.id.duplicateSectionTitle)
        val duplicateSelectionHint = contentView.findViewById<TextView>(
            R.id.duplicateSelectionHintText
        )
        val resetRecommended = contentView.findViewById<TextView>(
            R.id.duplicateResetRecommended
        )
        val duplicateMembersContainer = contentView.findViewById<LinearLayout>(
            R.id.duplicateMembersContainer
        )

        summaryText.text = getString(
            R.string.storage_cleaner_details_summary_format,
            details.category,
            details.reclaimableSize
        )
        reasonText.text = getString(R.string.storage_cleaner_details_why_flagged, details.reason)
        impactText.text = details.selectionImpact
        safetyHintText.text = getString(
            R.string.storage_cleaner_details_safety,
            details.safetyHint
        )

        fieldsSectionTitle.isVisible = details.fields.isNotEmpty()
        fieldsCard.isVisible = details.fields.isNotEmpty()
        details.fields.forEach { field ->
            val row = layoutInflater.inflate(
                R.layout.item_storage_cleaner_detail_field,
                fieldsContainer,
                false
            )
            row.findViewById<TextView>(R.id.detailFieldLabel).text = field.label
            row.findViewById<TextView>(R.id.detailFieldValue).text = field.value
            fieldsContainer.addView(row)
        }

        val hasDuplicateMembers = details.duplicateMembers.isNotEmpty()
        val keepSelectionRequiresReview =
            duplicateGroup?.keepSelectionRequiresReview == true
        duplicateSectionTitle.isVisible = hasDuplicateMembers
        duplicateSelectionHint.isVisible = hasDuplicateMembers
        duplicateSelectionHint.text = getString(
            if (keepSelectionRequiresReview) {
                R.string.storage_cleaner_duplicate_review_required_hint
            } else {
                R.string.storage_cleaner_choose_copy_to_keep
            }
        )
        val memberBindings = mutableListOf<
            Triple<StorageCleanerDuplicateMemberDetails, RadioButton, TextView>
            >()

        fun renderKeepSelection() {
            memberBindings.forEach { (member, keepRadio, statusText) ->
                val isKept = member.path == pendingKeepPath
                keepRadio.isChecked = isKept
                statusText.text = when {
                    isKept && keepSelectionRequiresReview ->
                        getString(R.string.storage_cleaner_duplicate_review_required)
                    isKept && member.isRecommendedCandidate ->
                        getString(R.string.storage_cleaner_duplicate_keep_recommended)
                    isKept -> getString(R.string.storage_cleaner_duplicate_keep_your_choice)
                    member.isRecommendedCandidate ->
                        getString(R.string.storage_cleaner_duplicate_recommended)
                    else -> getString(R.string.storage_cleaner_duplicate_will_remove)
                }
                statusText.alpha = if (isKept || member.isRecommendedCandidate) 1f else 0.72f
            }
            resetRecommended.isVisible = hasDuplicateMembers &&
                pendingKeepPath != duplicateGroup?.recommendedKeepCandidatePath
        }

        details.duplicateMembers.forEach { member ->
            val row = layoutInflater.inflate(
                R.layout.item_storage_cleaner_duplicate_member,
                duplicateMembersContainer,
                false
            )
            val file = File(member.path)
            val preview = row.findViewById<FileIconShapeView>(R.id.duplicateMemberPreview)
            val nameText = row.findViewById<TextView>(R.id.duplicateMemberName)
            val statusText = row.findViewById<TextView>(R.id.duplicateMemberStatus)
            val pathText = row.findViewById<TextView>(R.id.duplicateMemberPath)
            val metadataText = row.findViewById<TextView>(R.id.duplicateMemberMetadata)
            val keepRadio = row.findViewById<RadioButton>(R.id.duplicateMemberKeepRadio)
            val openFolderAction = row.findViewById<TextView>(R.id.duplicateMemberOpenFolder)
            val openAction = row.findViewById<TextView>(R.id.duplicateMemberOpen)

            nameText.text = file.name.ifBlank { member.path }
            pathText.text = file.parent ?: member.path
            metadataText.text = listOfNotNull(member.size, member.modifiedTime)
                .joinToString(" • ")
            bindDuplicateMemberPreview(preview, member.path)

            memberBindings += Triple(member, keepRadio, statusText)
            keepRadio.setOnClickListener {
                pendingKeepPath = member.path
                renderKeepSelection()
            }
            row.setOnClickListener {
                pendingKeepPath = member.path
                renderKeepSelection()
            }
            openAction.setOnClickListener { openFilePath(member.path) }
            openFolderAction.setOnClickListener { openContainingFolder(member.path) }
            duplicateMembersContainer.addView(row)
        }
        resetRecommended.setOnClickListener {
            pendingKeepPath = duplicateGroup?.recommendedKeepCandidatePath
            renderKeepSelection()
        }
        renderKeepSelection()

        val builder = AlertDialog.Builder(this)
            .setTitle(details.title)
            .setView(contentView)
            .setNegativeButton(
                if (hasDuplicateMembers) android.R.string.cancel else R.string.close,
                null
            )

        if (hasDuplicateMembers) {
            builder.setPositiveButton(R.string.save) { _, _ ->
                val selectedPath = pendingKeepPath ?: return@setPositiveButton
                if (viewModel.setDuplicateKeepCandidate(recommendation.id, selectedPath)) {
                    Snackbar.make(
                        binding.root,
                        R.string.storage_cleaner_keep_choice_saved,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        } else if (details.actions.canOpenFile) {
            builder
                .setNeutralButton(R.string.storage_cleaner_open_folder) { _, _ ->
                    details.path?.let(::openContainingFolder)
                }
                .setPositiveButton(R.string.storage_cleaner_open) { _, _ ->
                    details.path?.let(::openFilePath)
                }
        } else if (details.actions.canOpenAppInfo) {
            builder.setPositiveButton(R.string.storage_cleaner_open_app_info) { _, _ ->
                details.packageName?.let(::openAppInfo)
            }
        } else if (!details.path.isNullOrBlank()) {
            builder.setPositiveButton(R.string.storage_cleaner_copy_path) { _, _ ->
                clipboardManager.copyText(details.path, this)
            }
        }

        val dialog = builder.create()
        val detailsScrollView = contentView.findViewById<ScrollView>(
            R.id.detailsScrollView
        )
        dialog.setOnShowListener {
            detailsScrollView.post {
                detailsScrollView.requestFocus()
                detailsScrollView.scrollTo(0, 0)
            }
        }
        dialog.show()
    }

    private fun bindDuplicateMemberPreview(preview: FileIconShapeView, path: String) {
        preview.dispose()
        preview.setImageDrawable(null)
        preview.showBuiltInIcon()
        val visual = storageCleanerFileVisual(path) ?: return
        preview.setImageResource(visual.fallbackIconRes)
        if (visual.shouldLoadPreview && visual.requestData != null) {
            preview.load(visual.requestData) {
                size(128, 128)
                error(visual.fallbackIconRes)
                listener(
                    onError = { _, _ -> preview.showBuiltInIcon() },
                    onSuccess = { _, _ ->
                        if (visual.isAppIcon) {
                            preview.showAppIcon()
                        } else {
                            preview.showThumbnail()
                        }
                    }
                )
            }
        }
    }

    private fun openFilePath(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, R.string.storage_cleaner_file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val openIntent = OpenFileActivity.createIntent(Paths.get(path), MimeType.GENERIC)
        startActivitySafe(openIntent)
    }

    private fun openContainingFolder(path: String) {
        val parentPath = File(path).parentFile?.path
        if (parentPath.isNullOrBlank() || !File(parentPath).exists()) {
            Toast.makeText(this, R.string.storage_cleaner_folder_missing, Toast.LENGTH_SHORT).show()
            return
        }
        startActivitySafe(FileListActivity.createViewIntent(Paths.get(parentPath).toAppPath()))
    }

    private fun renderPermissionAndHintState(state: StorageCleanerUiState, hasUsageAccess: Boolean) {
        binding.openUsageAccessButton.isVisible = !hasUsageAccess
        val hints = buildStorageCleanerHintLines(
            missingCapabilities = state.analysis?.missingCapabilities.orEmpty(),
            stateMessage = state.message,
            hasUsageAccess = hasUsageAccess,
            usageAccessCapabilityPrefix = getString(
                R.string.storage_cleaner_usage_access_capability_prefix
            ),
            usageAccessMessage = getString(R.string.storage_cleaner_usage_access_required),
            limitedResultsPrefix = getString(
                R.string.storage_cleaner_limited_results_prefix
            )
        )
        binding.permissionHintText.text = hints.joinToString("\n")
        binding.permissionHintText.isVisible = hints.isNotEmpty()
    }

    private fun showDeletePreview() {
        lifecycleScope.launch {
            val items = viewModel.buildDeletePreview()
            val message = if (items.isEmpty()) {
                getString(R.string.storage_cleaner_no_selection)
            } else {
                val kept = items.mapNotNull { it.keepCandidatePath }
                    .distinct()
                    .joinToString("\n") { path ->
                        getString(
                            R.string.storage_cleaner_review_keeping,
                            File(path).name.ifBlank { path }
                        )
                    }
                val removing = items.take(50).joinToString("\n") {
                    "• ${it.displayName} (${Formatter.formatFileSize(this@StorageCleanerActivity, it.bytes)})"
                }
                listOf(kept, removing).filter(String::isNotBlank).joinToString("\n\n")
            }
            val builder = AlertDialog.Builder(this@StorageCleanerActivity)
                .setTitle(R.string.storage_cleaner_review_before_delete)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
            if (items.isNotEmpty()) {
                builder.setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        val operationId = viewModel.executeDeletion()
                        if (operationId != null) {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.transfer_section_queued) + " — " +
                                    getString(R.string.transfer_center_title),
                                Snackbar.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this@StorageCleanerActivity,
                                R.string.storage_cleaner_no_selection,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            builder.show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }


}
