package com.wisso.wizefiles.feature.transfer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ActivityTransferDetailBinding
import com.wisso.wizefiles.databinding.ItemTransferDetailBinding
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.sync.SyncAction
import com.wisso.wizefiles.feature.sync.SyncActionType
import com.wisso.wizefiles.feature.sync.SyncRepository
import com.wisso.wizefiles.feature.sync.SyncSide
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.util.showToast

class TransferDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransferDetailBinding
    private val operationId: String by lazy { requireNotNull(intent.getStringExtra(EXTRA_OPERATION_ID)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.itemList.layoutManager = LinearLayoutManager(this)
        binding.retryFailedButton.setOnClickListener {
            FileOperationService.resumeTransfer(operationId, this)
            render()
        }
        binding.openDestinationButton.setOnClickListener {
            val destination = TransferRepository.operation(operationId)
                ?.let(::resolveOpenDestination)
            if (destination == null) {
                showToast(R.string.file_list_location_unavailable)
            } else {
                startActivity(FileListActivity.createViewIntent(destination.toAppPath()))
            }
        }
        binding.clearHistoryButton.setOnClickListener {
            if (TransferRepository.deleteHistory(operationId)) finish()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
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

    private fun render() {
        val operation = TransferRepository.operation(operationId) ?: run {
            finish()
            return
        }
        title = getString(R.string.transfer_details)
        binding.summaryText.text = getString(
            R.string.transfer_detail_summary,
            operation.completedItems,
            operation.skippedItems,
            operation.failedItems,
            formatTransferPath(this, operation.destinationUri)
        )
        val items = TransferRepository.items(operationId)
        val syncActions = SyncRepository.runForTransfer(operationId)?.let { run ->
            SyncRepository.actions(run.id).filter { it.transferItemId > 0 }
                .associateBy(SyncAction::transferItemId)
        }.orEmpty()
        binding.itemList.adapter = TransferDetailAdapter(items, syncActions, operation.type)
        binding.retryFailedButton.isVisible = items.any { it.state == TransferItemState.FAILED }
        binding.openDestinationButton.isVisible = resolveOpenDestination(operation) != null
        binding.clearHistoryButton.isVisible = operation.state.isTerminal
    }

    companion object {
        private const val EXTRA_OPERATION_ID = "transfer.operation_id"

        fun createIntent(context: Context, operationId: String): Intent =
            Intent(context, TransferDetailActivity::class.java)
                .putExtra(EXTRA_OPERATION_ID, operationId)
    }
}

private class TransferDetailAdapter(
    private val items: List<TransferItemRecord>,
    private val syncActions: Map<Long, SyncAction>,
    private val operationType: TransferOperationType
) :
    RecyclerView.Adapter<TransferDetailAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemTransferDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.bind(item, syncActions[item.id], operationType)
    }

    class Holder(private val binding: ItemTransferDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: TransferItemRecord,
            syncAction: SyncAction?,
            operationType: TransferOperationType
        ) {
            binding.pathText.text = item.relativePath.ifEmpty { item.sourceUri }
            binding.resultText.text = buildString {
                syncAction?.let { action ->
                    append(actionDescription(action)).append(" • ")
                }
                append(
                    if (
                        operationType == TransferOperationType.DELETE &&
                        item.state == TransferItemState.COPIED
                    ) {
                        "deleted"
                    } else {
                        item.state.name.lowercase().replace('_', ' ')
                    }
                )
                if (item.errorMessage.isNotEmpty()) append(" • ").append(item.errorMessage)
            }
        }

        private fun actionDescription(action: SyncAction): String {
            val context = binding.root.context
            val side = context.getString(
                if (action.direction == SyncSide.SOURCE) {
                    R.string.sync_side_source
                } else {
                    R.string.sync_side_destination
                }
            )
            val template = when (action.type) {
                SyncActionType.COPY -> R.string.sync_history_copied
                SyncActionType.UPDATE -> R.string.sync_history_updated
                SyncActionType.MOVE -> R.string.sync_history_moved
                SyncActionType.PROTECT -> R.string.sync_history_protected
                SyncActionType.DELETE -> R.string.sync_history_deleted
                SyncActionType.CONFLICT -> R.string.sync_history_conflict
                SyncActionType.SKIP -> R.string.sync_history_skipped
            }
            return context.getString(template, side)
        }
    }
}
