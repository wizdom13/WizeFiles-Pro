package com.wisso.wizefiles.feature.transfer

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ItemTransferOperationBinding
import com.wisso.wizefiles.databinding.ItemTransferSectionBinding
import java.util.Date

internal class TransferCenterAdapter(
    private val onOpen: (TransferOperationRecord) -> Unit,
    private val onPrimary: (TransferOperationRecord) -> Unit,
    private val onSecondary: (TransferOperationRecord) -> Unit,
    private val canOpenDestination: (TransferOperationRecord) -> Boolean,
    private val onMove: (TransferOperationRecord, Int) -> Unit,
    private val onClearHistory: (TransferCenterSection) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: List<TransferCenterRow> = emptyList()

    fun submit(rows: List<TransferCenterRow>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is TransferCenterRow.Header -> VIEW_TYPE_HEADER
        is TransferCenterRow.Operation -> VIEW_TYPE_OPERATION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderHolder(ItemTransferSectionBinding.inflate(inflater, parent, false))
        } else {
            OperationHolder(ItemTransferOperationBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is TransferCenterRow.Header -> (holder as HeaderHolder).bind(row)
            is TransferCenterRow.Operation -> (holder as OperationHolder).bind(row.record)
        }
    }

    private inner class HeaderHolder(private val binding: ItemTransferSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: TransferCenterRow.Header) {
            binding.titleText.setText(row.titleRes)
            binding.clearButton.isVisible = row.clearSection != null
            binding.clearButton.setOnClickListener {
                row.clearSection?.let(onClearHistory)
            }
        }
    }

    private inner class OperationHolder(private val binding: ItemTransferOperationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(operation: TransferOperationRecord) = with(binding) {
            val context = root.context
            titleText.text = context.getString(
                R.string.transfer_operation_title,
                operation.type.displayName(),
                operation.state.displayName()
            )
            val destination = formatTransferPath(context, operation.destinationUri)
            destinationText.text = when {
                operation.type == TransferOperationType.DELETE &&
                    !operation.state.isTerminal &&
                    operation.currentItem.isNotEmpty() -> operation.currentItem
                operation.type == TransferOperationType.DELETE -> destination
                operation.currentItem.isNotEmpty() -> "${operation.currentItem} → $destination"
                else -> destination
            }
            val progressUi = operation.progressUi()
            val hasKnownBytes = operation.totalBytes > 0
            val hasKnownItems =
                operation.type == TransferOperationType.DELETE && operation.totalItems > 0
            progressBar.isIndeterminate =
                !hasKnownBytes && !hasKnownItems && !operation.state.isTerminal
            progressBar.max = 10_000
            progressBar.progress = when {
                progressUi.forceCompleteProgress -> 10_000
                hasKnownBytes -> {
                    ((operation.transferredBytes.coerceAtMost(operation.totalBytes) * 10_000) /
                        operation.totalBytes).toInt()
                }
                hasKnownItems -> {
                    ((progressUi.processedItems * 10_000) / operation.totalItems).toInt()
                }
                else -> 0
            }
            progressText.text = progressText(operation, progressUi)
            val timeRange = operation.terminalTimeRangeOrNull()
            timestampsText.isVisible = timeRange != null
            timestampsText.text = timeRange?.let { formatTimeRange(context, it) }
            val queued = TransferCenterPolicy.canReorder(operation.state)
            moveUpButton.isVisible = queued
            moveDownButton.isVisible = queued
            moveUpButton.setOnClickListener { onMove(operation, -1) }
            moveDownButton.setOnClickListener { onMove(operation, 1) }
            val primaryAction = resolveTransferCenterPrimaryAction(
                operation,
                canOpenDestination(operation)
            )
            primaryButton.isVisible = primaryAction != TransferCenterPrimaryAction.NONE
            primaryAction.labelResOrNull(operation.type)?.let(primaryButton::setText)
            primaryButton.setOnClickListener { onPrimary(operation) }
            secondaryButton.setText(when (TransferCenterPolicy.secondaryAction(operation.state)) {
                TransferCenterSecondaryAction.CANCEL -> android.R.string.cancel
                TransferCenterSecondaryAction.DETAILS -> R.string.transfer_details
            })
            secondaryButton.setOnClickListener { onSecondary(operation) }
            root.setOnClickListener { onOpen(operation) }
        }

        private fun progressText(
            operation: TransferOperationRecord,
            progressUi: TransferProgressUi
        ): String {
            val context = binding.root.context
            val items = context.getString(
                R.string.transfer_item_progress,
                progressUi.processedItems,
                operation.totalItems
            )
            if (!progressUi.showLiveMetrics) {
                return if (operation.state == TransferOperationState.QUEUED) {
                    items
                } else {
                    "$items • ${operation.state.displayName()}"
                }
            }

            val details = mutableListOf<String>()
            val activeItem = progressUi.activeItemNumber
            if (activeItem != null) {
                val activeVerbRes = when (operation.type) {
                        TransferOperationType.COPY -> R.string.transfer_action_copying
                        TransferOperationType.MOVE -> R.string.transfer_action_moving
                        TransferOperationType.DELETE -> null
                        TransferOperationType.EXTRACT -> R.string.transfer_action_extracting
                        TransferOperationType.BACKUP,
                        TransferOperationType.MIRROR,
                        TransferOperationType.TWO_WAY_SYNC,
                        TransferOperationType.NEARBY_SEND,
                        TransferOperationType.NEARBY_RECEIVE,
                        TransferOperationType.ARCHIVE_MODIFY,
                        TransferOperationType.APK_SIGN,
                        TransferOperationType.AAB_SIGN,
                        TransferOperationType.APKS_SIGN,
                        TransferOperationType.XAPK_SIGN,
                        TransferOperationType.APKM_IMPORT -> R.string.transfer_action_copying
                        TransferOperationType.MOVE_BACKUP -> R.string.transfer_action_moving
                    }
                if (activeVerbRes != null) {
                    details += context.getString(
                        R.string.transfer_active_item_progress,
                        context.getString(activeVerbRes),
                        activeItem,
                        operation.totalItems
                    )
                } else {
                    details += items
                }
            } else {
                details += items
            }
            if (operation.totalBytes > 0) {
                details += "${Formatter.formatFileSize(context, operation.transferredBytes)} of " +
                    Formatter.formatFileSize(context, operation.totalBytes)
            }
            if (operation.speedBytesPerSecond > 0) {
                details += Formatter.formatFileSize(context, operation.speedBytesPerSecond) + "/s"
                details += if (operation.etaSeconds >= 0) {
                    context.getString(R.string.transfer_eta_seconds, operation.etaSeconds)
                } else {
                    context.getString(R.string.transfer_calculating)
                }
            }
            return details.joinToString(" • ")
        }

        private fun formatTimeRange(context: Context, timeRange: TransferTimeRange): String {
            val dateFormat = DateFormat.getDateFormat(context)
            val timeFormat = DateFormat.getTimeFormat(context)
            fun format(timestampMillis: Long): String {
                val timestamp = Date(timestampMillis)
                return "${dateFormat.format(timestamp)} ${timeFormat.format(timestamp)}"
            }
            return "${format(timeRange.startedAtMillis)} → ${format(timeRange.endedAtMillis)}"
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_OPERATION = 1
    }
}

private fun TransferCenterPrimaryAction.labelResOrNull(type: TransferOperationType): Int? = when (this) {
    TransferCenterPrimaryAction.NONE -> null
    TransferCenterPrimaryAction.PAUSE -> R.string.transfer_pause
    TransferCenterPrimaryAction.RESUME -> R.string.transfer_resume
    TransferCenterPrimaryAction.RETRY -> R.string.transfer_retry
    TransferCenterPrimaryAction.OPEN_RESULT -> R.string.transfer_open_destination
    TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW ->
        if (TransferCenterPolicy.isSigning(type)) R.string.apk_signing_reenter_password
        else R.string.transfer_resume
}
