package com.wisso.wizefiles.feature.sync

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ActivitySyncPreviewBinding
import com.wisso.wizefiles.databinding.ItemSyncActionBinding
import com.wisso.wizefiles.feature.transfer.formatTransferPath
import java.util.concurrent.Executors

class SyncPreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncPreviewBinding
    private val executor = Executors.newSingleThreadExecutor()
    private var detailsExpanded = false
    private val runId: String
        get() = intent.getStringExtra(EXTRA_RUN_ID).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.actionList.layoutManager = LinearLayoutManager(this)
        refresh()
        binding.cancel.setOnClickListener {
            runCatching { SyncRepository.transitionRun(runId, SyncRunState.CANCELLED) }
            finish()
        }
        binding.approve.setOnClickListener { onPrimaryAction() }
        binding.detailsToggle.setOnClickListener { toggleScanDetails() }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun onPrimaryAction() {
        val run = SyncRepository.run(runId) ?: return
        if (isRetryableSyncBlock(run.safetyBlockReason)) {
            retryScan(run)
        } else {
            execute()
        }
    }

    private fun retryScan(blockedRun: SyncRun) {
        binding.cancel.isEnabled = false
        binding.approve.isEnabled = false
        binding.approve.text = getString(R.string.sync_planning)
        executor.execute {
            runCatching {
                SyncRepository.transitionRun(blockedRun.id, SyncRunState.CANCELLED)
                SyncRunCoordinator().plan(blockedRun.profileId, SyncRunTrigger.RETRY)
            }.onSuccess { planned ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    intent.putExtra(EXTRA_RUN_ID, planned.run.id)
                    detailsExpanded = false
                    refresh()
                    binding.overviewBar.setExpanded(true, false)
                    binding.actionList.scrollToPosition(0)
                }
            }.onFailure { error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Toast.makeText(
                        this,
                        error.message ?: getString(R.string.sync_planning_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun execute() {
        binding.approve.isEnabled = false
        binding.approve.text = getString(R.string.sync_running)
        runCatching {
            val run = requireNotNull(SyncRepository.run(runId))
            if (run.state == SyncRunState.NEEDS_ATTENTION) {
                check(SyncRepository.actions(runId).none {
                    it.state == SyncActionState.BLOCKED
                })
                SyncRepository.transitionRun(runId, SyncRunState.QUEUED)
            } else {
                SyncRunCoordinator().approve(runId)
            }
            SyncResumeWorker.enqueue(this, runId)
        }.onSuccess {
            Toast.makeText(this, R.string.sync_queued, Toast.LENGTH_SHORT).show()
            finish()
        }.onFailure { error ->
            refresh()
            Toast.makeText(
                this,
                error.message ?: getString(R.string.sync_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun refresh() {
        binding.cancel.isEnabled = true
        val actions = SyncRepository.actions(runId)
        val run = SyncRepository.run(runId)
        binding.actionList.adapter = SyncActionAdapter(actions, ::resolveConflict)
        binding.summary.text = summary(actions)
        binding.bytes.text = if (run == null) {
            ""
        } else {
            getString(
                R.string.sync_preview_bytes,
                Formatter.formatFileSize(this, run.plannedTransferBytes),
                Formatter.formatFileSize(this, run.plannedProtectedBytes)
            )
        }

        val reason = run?.safetyBlockReason.orEmpty()
        val partialPreview = reason == "SCAN_INCOMPLETE"
        binding.statusCard.isVisible = reason.isNotBlank()
        binding.partialNotice.isVisible = partialPreview
        binding.safetyMessage.text = if (partialPreview) {
            getString(R.string.sync_safety_scan_incomplete_retry)
        } else if (reason.isNotBlank()) {
            safetyMessage(reason)
        } else {
            ""
        }
        val details = run?.let(::scanDetails).orEmpty()
        if (details.isBlank()) detailsExpanded = false
        binding.detailsToggle.isVisible = details.isNotBlank()
        binding.detailsToggle.setText(if (detailsExpanded) R.string.hide else R.string.show)
        binding.safetyDetails.text = details
        binding.safetyDetails.isVisible = details.isNotBlank() && detailsExpanded
        binding.actionList.alpha = if (partialPreview) PARTIAL_PREVIEW_ALPHA else 1f

        val unresolvedActions = actions.any { it.state == SyncActionState.BLOCKED }
        val retryableBlock = isRetryableSyncBlock(reason)
        binding.approve.isEnabled = retryableBlock || !unresolvedActions
        binding.approve.text = getString(
            when {
                retryableBlock -> R.string.sync_retry_scan
                unresolvedActions -> R.string.sync_needs_attention
                else -> R.string.sync_approve_run
            }
        )
    }

    private fun toggleScanDetails() {
        detailsExpanded = !detailsExpanded
        binding.detailsToggle.setText(if (detailsExpanded) R.string.hide else R.string.show)
        binding.safetyDetails.isVisible = detailsExpanded && binding.safetyDetails.text.isNotBlank()
    }

    private fun scanDetails(run: SyncRun): String =
        decodeSyncScanDiagnostics(run.safetyBlockDetails)
            .joinToString("\n\n") { diagnostic ->
                val side = diagnostic.side?.let(::sideLabel)
                val endpoint = diagnostic.endpointUri.takeIf(String::isNotBlank)?.let { uri ->
                    runCatching { formatTransferPath(this, uri) }.getOrDefault(uri)
                }
                if (side != null && endpoint != null) {
                    getString(
                        R.string.sync_scan_detail_format,
                        side,
                        endpoint,
                        diagnostic.detail
                    )
                } else {
                    diagnostic.detail
                }
            }

    private fun sideLabel(side: SyncSide): String = getString(
        when (side) {
            SyncSide.SOURCE -> R.string.sync_side_source
            SyncSide.DESTINATION -> R.string.sync_side_destination
        }
    ).replaceFirstChar(Char::uppercase)

    private fun resolveConflict(action: SyncAction) {
        if (action.type != SyncActionType.CONFLICT) return
        val labels = arrayOf(
            getString(R.string.sync_conflict_use_source),
            getString(R.string.sync_conflict_use_destination),
            getString(R.string.sync_conflict_skip)
        )
        val values = arrayOf("SOURCE", "DESTINATION", "SKIP")
        MaterialAlertDialogBuilder(this)
            .setTitle(action.relativePath)
            .setItems(labels) { _, index ->
                SyncRepository.resolveConflict(action, values[index])
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun summary(actions: List<SyncAction>): String {
        fun count(type: SyncActionType) = actions.count { it.type == type }
        return getString(
            R.string.sync_preview_summary,
            count(SyncActionType.COPY),
            count(SyncActionType.UPDATE),
            count(SyncActionType.MOVE),
            count(SyncActionType.PROTECT),
            count(SyncActionType.DELETE),
            count(SyncActionType.CONFLICT),
            count(SyncActionType.SKIP)
        )
    }

    private fun safetyMessage(reason: String): String = getString(
        when (reason) {
            "SCAN_INCOMPLETE" -> R.string.sync_safety_scan_incomplete_retry
            "STORAGE_IDENTITY_CHANGED" -> R.string.sync_safety_storage_changed
            "SOURCE_UNEXPECTEDLY_EMPTY" -> R.string.sync_safety_source_empty
            "DELETION_COUNT_LIMIT" -> R.string.sync_safety_deletion_count
            "DELETION_SIZE_LIMIT" -> R.string.sync_safety_deletion_size
            "DELETION_RATIO_LIMIT" -> R.string.sync_safety_deletion_ratio
            else -> R.string.sync_safety_blocked
        }
    )

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    companion object {
        private const val EXTRA_RUN_ID = "sync_run_id"
        private const val PARTIAL_PREVIEW_ALPHA = 0.62f

        fun createIntent(context: Context, runId: String) =
            Intent(context, SyncPreviewActivity::class.java).putExtra(EXTRA_RUN_ID, runId)
    }
}

internal fun isRetryableSyncBlock(reason: String): Boolean =
    reason == "SCAN_INCOMPLETE" || reason == "STORAGE_IDENTITY_CHANGED"

private class SyncActionAdapter(
    private val actions: List<SyncAction>,
    private val onAction: (SyncAction) -> Unit
) :
    RecyclerView.Adapter<SyncActionAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSyncActionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onAction
    )

    override fun getItemCount() = actions.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(actions[position])

    class Holder(
        private val binding: ItemSyncActionBinding,
        private val onAction: (SyncAction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(action: SyncAction) {
            binding.action.text = action.type.name.lowercase().replaceFirstChar(Char::uppercase)
            binding.path.text = action.relativePath
            binding.root.setOnClickListener { onAction(action) }
        }
    }
}
