package com.wisso.wizefiles.feature.transfer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ActivityTransferCenterBinding
import com.wisso.wizefiles.feature.apksigning.ApkSignVerifyActivity
import com.wisso.wizefiles.feature.apksigning.AabSignVerifyActivity
import com.wisso.wizefiles.feature.apksigning.ApksSignVerifyActivity
import com.wisso.wizefiles.feature.apksigning.XapkSignVerifyActivity
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.nearby.NearbyTransferActivity
import com.wisso.wizefiles.feature.nearby.NearbyTransferService
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.util.showToast

class TransferCenterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransferCenterBinding
    private lateinit var adapter: TransferCenterAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var filter = TransferCenterFilter.ALL

    private val actionDispatcher by lazy {
        TransferCenterActionDispatcher(
            pauseFileOperation = FileOperationService::pauseTransfer,
            resumeFileOperation = { FileOperationService.resumeTransfer(it, this) },
            cancelFileOperation = FileOperationService::cancelTransfer,
            pauseNearby = { NearbyTransferService.pauseOperation(this, it) },
            cancelNearby = { NearbyTransferService.cancelOperation(this, it) },
            openNearbyRecovery = {
                startActivity(NearbyTransferActivity.createResumeIntent(this, it))
            },
            openSigningRecovery = ::openSigningRecovery,
            openResult = ::openDestination,
            openDetails = ::openDetail
        )
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        adapter = TransferCenterAdapter(
            onOpen = { openDetail(it.id) },
            onPrimary =(::onPrimaryAction),
            onSecondary =(::onSecondaryAction),
            canOpenDestination = ::canOpenDestination,
            onMove =(::moveQueued),
            onClearHistory = ::clearHistory
        )
        binding.transferList.layoutManager = LinearLayoutManager(this)
        binding.transferList.adapter = adapter
        binding.filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.filterRunning -> TransferCenterFilter.RUNNING
                R.id.filterQueued -> TransferCenterFilter.QUEUED
                R.id.filterPaused -> TransferCenterFilter.PAUSED
                R.id.filterCompleted -> TransferCenterFilter.COMPLETED
                R.id.filterFailed -> TransferCenterFilter.FAILED
                else -> TransferCenterFilter.ALL
            }
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
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

    private fun refresh() {
        val rows = buildRows(TransferDatabase.operations(), filter)
        adapter.submit(rows)
        binding.emptyText.isVisible = rows.isEmpty()
    }

    private fun onPrimaryAction(operation: TransferOperationRecord) {
        val action = resolveTransferCenterPrimaryAction(operation, canOpenDestination(operation))
        if (actionDispatcher.primary(operation, action)) refresh()
    }

    private fun onSecondaryAction(operation: TransferOperationRecord) {
        if (actionDispatcher.secondary(operation)) refresh()
    }

    private fun clearHistory(section: TransferCenterSection) {
        val removed = when (section) {
            TransferCenterSection.COMPLETED -> TransferRepository.clearCompletedHistory()
            TransferCenterSection.FAILED -> TransferRepository.clearFailedHistory()
            else -> return
        }
        if (removed > 0) refresh()
    }

    private fun moveQueued(operation: TransferOperationRecord, direction: Int) {
        val queued = TransferDatabase.operations(setOf(TransferOperationState.QUEUED)).toMutableList()
        val index = queued.indexOfFirst { it.id == operation.id }
        val destination = index + direction
        if (index < 0 || destination !in queued.indices) return
        val moved = queued.removeAt(index)
        queued.add(destination, moved)
        TransferRepository.reorderQueued(queued.map(TransferOperationRecord::id))
        refresh()
    }

    private fun canOpenDestination(operation: TransferOperationRecord): Boolean =
        resolveOpenDestination(operation) != null

    private fun openDestination(operation: TransferOperationRecord) {
        val destination = resolveOpenDestination(operation)
        if (destination == null) {
            showToast(R.string.file_list_location_unavailable)
            return
        }
        startActivity(FileListActivity.createViewIntent(destination.toAppPath()))
    }

    private fun openDetail(operationId: String) {
        startActivity(TransferDetailActivity.createIntent(this, operationId))
    }

    private fun openSigningRecovery(type: TransferOperationType, operationId: String) {
        val intent = when (type) {
            TransferOperationType.APK_SIGN -> ApkSignVerifyActivity.createResumeIntent(this, operationId)
            TransferOperationType.AAB_SIGN -> AabSignVerifyActivity.createResumeIntent(this, operationId)
            TransferOperationType.APKS_SIGN -> ApksSignVerifyActivity.createResumeIntent(this, operationId)
            TransferOperationType.XAPK_SIGN -> XapkSignVerifyActivity.createResumeIntent(this, operationId)
            else -> return
        }
        startActivity(intent)
    }

    companion object {
        private const val REFRESH_INTERVAL_MILLIS = 1_000L
    }
}

internal sealed class TransferCenterRow {
    data class Header(
        val titleRes: Int,
        val clearSection: TransferCenterSection? = null
    ) : TransferCenterRow()
    data class Operation(val record: TransferOperationRecord) : TransferCenterRow()
}

internal fun buildRows(
    operations: List<TransferOperationRecord>,
    filter: TransferCenterFilter
): List<TransferCenterRow> {
    val groups = listOf(
        TransferCenterSection.RUNNING to R.string.transfer_section_running,
        TransferCenterSection.QUEUED to R.string.transfer_section_queued,
        TransferCenterSection.PAUSED to R.string.transfer_section_paused,
        TransferCenterSection.COMPLETED to R.string.transfer_section_completed,
        TransferCenterSection.FAILED to R.string.transfer_section_failed
    )
    return buildList {
        for ((section, title) in groups) {
            val matching = operations.filter {
                TransferCenterPolicy.section(it.state) == section &&
                    TransferCenterPolicy.matches(filter, it.state)
            }
            if (matching.isEmpty()) continue
            add(
                TransferCenterRow.Header(
                    titleRes = title,
                    clearSection = section.takeIf(TransferCenterPolicy::canClearHistory)
                )
            )
            matching.forEach { add(TransferCenterRow.Operation(it)) }
        }
    }
}
