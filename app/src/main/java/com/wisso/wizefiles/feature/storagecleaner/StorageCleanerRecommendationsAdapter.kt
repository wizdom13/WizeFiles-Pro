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

internal class StorageCleanerRecommendationsAdapter(
    private val onChecked: (String, Boolean) -> Unit,
    private val onSectionSelectionToggle: (Collection<String>, Boolean) -> Unit,
    private val onOpenUnusedApp: (String) -> Unit,
    private val onUninstallUnusedApp: (String) -> Unit,
    private val onItemClick: (CleanupRecommendation) -> Unit,
    private val onIgnore: (CleanupRecommendation) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<StorageCleanerListItem>()
    private var selectedIds: Set<String> = emptySet()
    private var recommendations: List<CleanupRecommendation> = emptyList()
    private var formatSize: (Long) -> String = Long::toString
    private var sectionSummary: (List<CleanupRecommendation>) -> String = { "" }
    private var colorForRecommendation: (CleanupRecommendation) -> Int = { android.graphics.Color.TRANSPARENT }
    private var colorForSection: (SectionKey) -> Int = { android.graphics.Color.TRANSPARENT }
    private val expandedSections = mutableMapOf<SectionKey, Boolean>()

    fun submit(
        recommendations: List<CleanupRecommendation>,
        selectedIds: Set<String>,
        formatSize: (Long) -> String,
        colorForRecommendation: (CleanupRecommendation) -> Int,
        colorForSection: (SectionKey) -> Int,
        sectionSummary: (List<CleanupRecommendation>) -> String
    ) {
        this.recommendations = recommendations
        this.selectedIds = selectedIds
        this.formatSize = formatSize
        this.colorForRecommendation = colorForRecommendation
        this.colorForSection = colorForSection
        this.sectionSummary = sectionSummary
        syncSectionExpansion(recommendations)
        rebuildItems()
    }

    private fun syncSectionExpansion(recommendations: List<CleanupRecommendation>) {
        val visibleSections = recommendations.map { it.toSectionKey() }.toSet()
        expandedSections.keys.retainAll(visibleSections)
        visibleSections.forEach { key -> expandedSections.putIfAbsent(key, false) }
    }

    private fun rebuildItems() {
        items.clear()
        items.addAll(recommendations.toSectionedList(expandedSections, selectedIds, sectionSummary))
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is StorageCleanerListItem.SectionHeader -> VIEW_TYPE_HEADER
        is StorageCleanerListItem.RecommendationRow -> VIEW_TYPE_RECOMMENDATION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_storage_cleaner_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_storage_cleaner_recommendation, parent, false)
                RecommendationViewHolder(view as ViewGroup)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is StorageCleanerListItem.SectionHeader -> {
                (holder as HeaderViewHolder).bind(
                    item = item,
                    dotColor = colorForSection(item.key),
                    onToggleSection = { key ->
                        expandedSections[key] = !(expandedSections[key] ?: false)
                        rebuildItems()
                    },
                    onToggleSelection = { ids, selected ->
                        onSectionSelectionToggle(ids, selected)
                    }
                )
            }
            is StorageCleanerListItem.RecommendationRow -> {
                (holder as RecommendationViewHolder).bind(
                    item.recommendation,
                    selectedIds.contains(item.recommendation.id),
                    onChecked,
                    onOpenUnusedApp,
                    onUninstallUnusedApp,
                    onItemClick,
                    onIgnore,
                    colorForRecommendation(item.recommendation)
                )
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val colorDot = view.findViewById<View>(R.id.sectionColorDot)
        private val title = view.findViewById<TextView>(R.id.sectionTitleText)
        private val summary = view.findViewById<TextView>(R.id.sectionSummaryText)
        private val selectToggle = view.findViewById<TextView>(R.id.sectionSelectToggleText)
        private val chevron = view.findViewById<ImageView>(R.id.sectionChevronImage)

        fun bind(
            item: StorageCleanerListItem.SectionHeader,
            dotColor: Int,
            onToggleSection: (SectionKey) -> Unit,
            onToggleSelection: (Collection<String>, Boolean) -> Unit
        ) {
            colorDot.background?.mutate()?.setTint(dotColor)
            title.text = item.key.localizedTitle(itemView.context)
            summary.text = item.summary
            summary.isVisible = item.summary.isNotBlank()
            chevron.setImageResource(
                if (item.expanded) R.drawable.ic_bs_chevron_up_control_normal_24dp
                else R.drawable.ic_bs_chevron_down_control_normal_24dp
            )
            val hasChildren = item.childRecommendationIds.isNotEmpty()
            selectToggle.isVisible = hasChildren
            if (hasChildren) {
                val selectAll = !item.areAllChildrenSelected
                selectToggle.text = itemView.context.getString(
                    if (selectAll) R.string.select_all else R.string.select_none
                )
                selectToggle.setOnClickListener {
                    onToggleSelection(item.childRecommendationIds, selectAll)
                }
            } else {
                selectToggle.setOnClickListener(null)
            }
            chevron.setOnClickListener { onToggleSection(item.key) }
            itemView.setOnClickListener { onToggleSection(item.key) }
        }
    }

    class RecommendationViewHolder(view: ViewGroup) : RecyclerView.ViewHolder(view) {
        private val preview = view.findViewById<FileIconShapeView>(R.id.previewImage)
        private val categoryColorDot = view.findViewById<View>(R.id.categoryColorDot)
        private val reason = view.findViewById<TextView>(R.id.reasonText)
        private val metadata = view.findViewById<TextView>(R.id.metadataText)
        private val checkbox = view.findViewById<CheckBox>(R.id.selectCheckBox)
        private val moreActions = view.findViewById<ImageButton>(R.id.moreActions)
        private val appAction = view.findViewById<TextView>(R.id.appActionText)

        fun bind(
            item: CleanupRecommendation,
            selected: Boolean,
            onChecked: (String, Boolean) -> Unit,
            onOpenUnusedApp: (String) -> Unit,
            onUninstallUnusedApp: (String) -> Unit,
            onItemClick: (CleanupRecommendation) -> Unit,
            onIgnore: (CleanupRecommendation) -> Unit,
            dotColor: Int
        ) {
            val context = itemView.context
            val filePath = item.path ?: item.duplicateGroup?.keepCandidatePath
            categoryColorDot.background?.mutate()?.setTint(dotColor)
            reason.text = item.reason
            metadata.text = buildMetadataText(
                sizeText = Formatter.formatFileSize(context, item.reclaimableBytes),
                filePath = filePath
            )
            metadata.isVisible = metadata.text.isNotEmpty()

            bindVisual(item, filePath)

            checkbox.setOnCheckedChangeListener(null)
            checkbox.isVisible = item.isDeletionCandidate
            checkbox.isChecked = selected && item.isDeletionCandidate
            if (item.isDeletionCandidate) {
                checkbox.setOnCheckedChangeListener { _, checked ->
                    onChecked(item.id, checked)
                }
            }
            appAction.visibility = if (item.packageName != null) TextView.VISIBLE else TextView.GONE
            appAction.setOnClickListener { item.packageName?.let(onOpenUnusedApp) }
            moreActions.setOnClickListener {
                PopupMenu(context, moreActions).apply {
                    menu.add(
                        Menu.NONE,
                        MENU_DETAILS,
                        Menu.NONE,
                        R.string.transfer_details
                    )
                    if (item.type == RecommendationType.UNUSED_APP &&
                        !item.packageName.isNullOrBlank()
                    ) {
                        menu.add(
                            Menu.NONE,
                            MENU_UNINSTALL_APP,
                            Menu.NONE,
                            R.string.storage_cleaner_uninstall_app
                        )
                    }
                    menu.add(
                        Menu.NONE,
                        MENU_IGNORE,
                        Menu.NONE,
                        R.string.storage_cleaner_ignore
                    )
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            MENU_DETAILS -> {
                                onItemClick(item)
                                true
                            }
                            MENU_UNINSTALL_APP -> {
                                item.packageName?.let(onUninstallUnusedApp)
                                true
                            }
                            MENU_IGNORE -> {
                                onIgnore(item)
                                true
                            }
                            else -> false
                        }
                    }
                    show()
                }
            }
            itemView.setOnClickListener { onItemClick(item) }
        }

        private fun bindVisual(item: CleanupRecommendation, filePath: String?) {
            val context = itemView.context
            preview.dispose()
            preview.isVisible = true
            preview.setImageDrawable(null)
            preview.showBuiltInIcon()

            val fileVisual = storageCleanerFileVisual(filePath)
            if (fileVisual != null) {
                preview.setImageResource(fileVisual.fallbackIconRes)
                if (fileVisual.shouldLoadPreview && fileVisual.requestData != null) {
                    preview.load(fileVisual.requestData) {
                        size(128, 128)
                        error(fileVisual.fallbackIconRes)
                        listener(
                            onError = { _, _ -> preview.showBuiltInIcon() },
                            onSuccess = { _, _ ->
                                if (fileVisual.isAppIcon) {
                                    preview.showAppIcon()
                                } else {
                                    preview.showThumbnail()
                                }
                            }
                        )
                    }
                }
                return
            }

            val visualSpec = recommendationVisualSpec(item)
            if (visualSpec.shouldUsePackageIcon) {
                val appIcon = runCatching {
                    context.packageManager.getApplicationIcon(item.packageName!!)
                }.getOrNull()
                if (appIcon != null) {
                    preview.showAppIcon()
                    preview.setImageDrawable(appIcon)
                    return
                }
            }
            preview.setImageResource(visualSpec.fallbackIconRes)
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_RECOMMENDATION = 1
        const val MENU_DETAILS = 1
        const val MENU_IGNORE = 2
        const val MENU_UNINSTALL_APP = 3
    }
}
