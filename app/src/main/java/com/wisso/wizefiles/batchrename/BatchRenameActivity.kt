// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.batchrename

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.databinding.ActivityBatchRenameBinding
import com.wisso.wizefiles.databinding.ItemBatchRenamePreviewBinding
import com.wisso.wizefiles.feature.filebrowser.name
import com.wisso.wizefiles.feature.filejobs.BatchRenameOperation
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.getPackageArchiveInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchRenameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBatchRenameBinding
    private lateinit var sessionId: String
    private lateinit var session: BatchRenameSessionStore.Session
    private val previewAdapter = PreviewAdapter()
    private var currentPlan: BatchRenamePlan? = null
    private var suppressInvalidation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val restoredSession = BatchRenameSessionStore.get(sessionId)
        if (restoredSession == null) {
            Toast.makeText(this, R.string.batch_rename_selection_expired, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        session = restoredSession
        binding = ActivityBatchRenameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.batch_rename_title)
        binding.selectionCountText.text = resources.getQuantityString(
            R.plurals.batch_rename_selected_count,
            session.files.size,
            session.files.size
        )
        binding.previewList.layoutManager = LinearLayoutManager(this)
        binding.previewList.adapter = previewAdapter
        binding.renameButton.isEnabled = false

        suppressInvalidation = true
        binding.patternEdit.setText(BatchRenamePatternEngine.defaultPattern(session.files.size))
        binding.startNumberEdit.setText(DEFAULT_START_NUMBER.toString())
        suppressInvalidation = false

        val invalidatingWatcher = object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) =
                invalidatePreview()
            override fun afterTextChanged(editable: Editable?) = Unit
        }
        listOf(
            binding.patternEdit,
            binding.replaceEdit,
            binding.replacementEdit,
            binding.startNumberEdit
        ).forEach { it.addTextChangedListener(invalidatingWatcher) }
        binding.regexSwitch.setOnCheckedChangeListener { _, _ -> invalidatePreview() }

        mapOf(
            binding.tokenNumber to "#",
            binding.tokenBaseName to "%n",
            binding.tokenFullName to "%N",
            binding.tokenExtension to "%E",
            binding.tokenDate to "%D",
            binding.tokenTime to "%T",
            binding.tokenSize to "%S",
            binding.tokenApkName to "%A",
            binding.tokenApkVersion to "%V"
        ).forEach { (chip, token) -> chip.setOnClickListener { insertPatternToken(token) } }

        binding.previewButton.setOnClickListener { buildPreview() }
        binding.renameButton.setOnClickListener { confirmRename() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        if (isFinishing) {
            BatchRenameSessionStore.remove(sessionId)
        }
        super.onDestroy()
    }

    private fun setupInsets() {
        val initialToolbarTopPadding = binding.toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = initialToolbarTopPadding + statusInsets.top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.toolbar)

        val initialActionBarBottomPadding = binding.renameActionBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.renameActionBar) { view, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = initialActionBarBottomPadding + navigationInsets.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.renameActionBar)
    }

    private fun insertPatternToken(token: String) {
        val editable = binding.patternEdit.text ?: return
        val start = binding.patternEdit.selectionStart.coerceAtLeast(0)
        val end = binding.patternEdit.selectionEnd.coerceAtLeast(start)
        editable.replace(start, end, token)
        binding.patternEdit.setSelection(start + token.length)
        binding.patternEdit.requestFocus()
    }

    private fun invalidatePreview() {
        if (suppressInvalidation) {
            return
        }
        currentPlan = null
        previewAdapter.submit(emptyList())
        binding.patternLayout.helperText = null
        binding.previewSummaryText.setText(R.string.batch_rename_preview_required)
        binding.renameButton.isEnabled = false
    }

    private fun buildPreview() {
        val startNumber = binding.startNumberEdit.text?.toString()?.toLongOrNull()
        if (startNumber == null || startNumber < 0) {
            binding.startNumberLayout.error = getString(R.string.batch_rename_invalid_start_number)
            return
        }
        binding.startNumberLayout.error = null
        binding.patternLayout.error = null
        binding.patternLayout.helperText = null
        binding.previewButton.isEnabled = false
        binding.previewProgress.show()
        val options = BatchRenameOptions(
            pattern = binding.patternEdit.text?.toString().orEmpty(),
            replaceText = binding.replaceEdit.text?.toString().orEmpty(),
            replacementText = binding.replacementEdit.text?.toString().orEmpty(),
            regexReplacement = binding.regexSwitch.isChecked,
            startNumber = startNumber
        )
        val needsApkMetadata = options.pattern.contains("%A") || options.pattern.contains("%V")
        lifecycleScope.launch {
            val previewInput = withContext(Dispatchers.IO) {
                val sources = session.files.map { file ->
                    val apkMetadata = if (needsApkMetadata && file.mimeType.isApk) {
                        loadApkMetadata(file.path)
                    } else {
                        null
                    }
                    BatchRenameSource(
                        originalName = file.name,
                        modifiedEpochMillis = file.attributes.lastModifiedEpochMillis,
                        sizeBytes = file.attributes.sizeBytes ?: 0L,
                        apkLabel = apkMetadata?.first,
                        apkVersionName = apkMetadata?.second
                    )
                }
                sources to loadExistingNames()
            }
            val plan = BatchRenamePatternEngine.plan(
                previewInput.first,
                options,
                previewInput.second
            )
            binding.previewProgress.hide()
            binding.previewButton.isEnabled = true
            if (plan.globalError != null) {
                binding.patternLayout.error = plan.globalError
                currentPlan = null
                previewAdapter.submit(emptyList())
                binding.renameButton.isEnabled = false
                return@launch
            }
            currentPlan = plan
            previewAdapter.submit(plan.rows)
            binding.previewList.post { binding.previewList.scrollToPosition(0) }
            binding.patternLayout.helperText = if (plan.changesExtensions()) {
                getString(R.string.batch_rename_extension_warning)
            } else {
                null
            }
            val errorCount = plan.rows.count { it.error != null }
            binding.previewSummaryText.text = when {
                errorCount > 0 -> resources.getQuantityString(
                    R.plurals.batch_rename_preview_error_count,
                    errorCount,
                    errorCount
                )
                plan.changedCount == 0 -> getString(R.string.batch_rename_nothing_to_change)
                else -> resources.getQuantityString(
                    R.plurals.batch_rename_preview_ready_count,
                    plan.changedCount,
                    plan.changedCount
                )
            }
            binding.renameButton.isEnabled = plan.isValid && plan.changedCount > 0
        }
    }

    private fun BatchRenamePlan.changesExtensions(): Boolean = rows.any { row ->
        val originalExtension = BatchRenamePatternEngine.splitName(row.originalName).second
        val targetExtension = BatchRenamePatternEngine.splitName(row.targetName).second
        originalExtension.isNotEmpty() &&
            !originalExtension.equals(targetExtension, ignoreCase = true)
    }

    private fun loadApkMetadata(path: com.wisso.wizefiles.storage.path.AppPath): Pair<String, String?>? =
        runCatching {
            val (packageInfo, closeable) = packageManager.getPackageArchiveInfoCompat(path, 0)
            closeable.use {
                val info = packageInfo ?: return@runCatching null
                val applicationInfo = info.applicationInfo ?: return@runCatching null
                applicationInfo.loadLabel(packageManager).toString() to info.versionName
            }
        }.getOrNull()

    private fun loadExistingNames(): List<String> {
        val parent = session.files.firstOrNull()?.path?.toLegacyPathOrNull()?.parent
            ?: return session.existingNames
        val directoryNames = runCatching {
            java.nio.file.Files.newDirectoryStream(parent).use { stream ->
                stream.map { it.fileName.toString() }.toList()
            }
        }.getOrDefault(emptyList())
        return (session.existingNames + directoryNames).distinct()
    }

    private fun confirmRename() {
        val plan = currentPlan?.takeIf { it.isValid && it.changedCount > 0 } ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.batch_rename_confirm_title)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.batch_rename_confirm_message,
                    plan.changedCount,
                    plan.changedCount
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.rename) { _, _ -> executeRename(plan) }
            .show()
    }

    private fun executeRename(plan: BatchRenamePlan) {
        val operations = plan.rows.filter(BatchRenamePreviewRow::isChanged).mapNotNull { row ->
            session.files.getOrNull(row.sourceIndex)?.path?.toLegacyPathOrNull()?.let { path ->
                BatchRenameOperation(path, row.targetName)
            }
        }
        if (operations.size != plan.changedCount) {
            Toast.makeText(this, R.string.batch_rename_path_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        FileOperationService.batchRename(operations, this)
        Toast.makeText(this, R.string.batch_rename_started, Toast.LENGTH_SHORT).show()
        BatchRenameSessionStore.remove(sessionId)
        finish()
    }

    private class PreviewAdapter : RecyclerView.Adapter<PreviewViewHolder>() {
        private val rows = mutableListOf<BatchRenamePreviewRow>()

        fun submit(newRows: List<BatchRenamePreviewRow>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder =
            PreviewViewHolder(
                ItemBatchRenamePreviewBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) =
            holder.bind(rows[position])

        override fun getItemCount(): Int = rows.size
    }

    private class PreviewViewHolder(
        private val binding: ItemBatchRenamePreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: BatchRenamePreviewRow) {
            binding.originalNameText.text = row.originalName
            binding.newNameText.text = row.targetName
            binding.statusText.text = row.error ?: if (row.isChanged) {
                binding.root.context.getString(R.string.batch_rename_status_ready)
            } else {
                binding.root.context.getString(R.string.batch_rename_status_unchanged)
            }
            binding.statusText.isEnabled = row.error == null
        }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "batch_rename_session_id"
        private const val DEFAULT_START_NUMBER = 1L

        fun createIntent(context: Context, sessionId: String): Intent =
            Intent(context, BatchRenameActivity::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
    }
}
