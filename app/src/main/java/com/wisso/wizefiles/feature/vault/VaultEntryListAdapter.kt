package com.wisso.wizefiles.vault

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.foregroundCompat
import com.wisso.wizefiles.core.android.compat.getDrawableCompat
import com.wisso.wizefiles.core.files.extensions.formatShort
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.model.asFileSize
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.core.files.mime.iconRes
import com.wisso.wizefiles.databinding.ItemVaultEntryGridBinding
import com.wisso.wizefiles.databinding.ItemVaultEntryListBinding
import com.wisso.wizefiles.feature.filebrowser.FileViewType
import com.wisso.wizefiles.ui.CheckableForegroundLinearLayout
import com.wisso.wizefiles.ui.CheckableItemBackground
import com.wisso.wizefiles.ui.FileIconShapeView
import com.wisso.wizefiles.ui.RecyclerItemAnimationHelper
import com.wisso.wizefiles.util.isMaterial3Theme
import com.wisso.wizefiles.util.getColorByAttr
import com.wisso.wizefiles.util.layoutInflater
import java.time.Instant

class VaultEntryListAdapter(
    private val onClick: (VaultEntry) -> Unit,
    private val onLongClick: (VaultEntry) -> Unit
) : ListAdapter<VaultEntry, VaultEntryListAdapter.ViewHolder>(DIFF_CALLBACK) {

    var viewType: FileViewType = FileViewType.LIST
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var directoryItemCounts: Map<String, Int> = emptyMap()
        set(value) {
            if (field == value) return
            field = value.toMap()
            notifyDataSetChanged()
        }

    var selectedEntryIds: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value.toSet()
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = viewType.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, itemViewType: Int): ViewHolder {
        val viewType = FileViewType.entries[itemViewType]
        val inflater = parent.context.layoutInflater
        val holder = when (viewType) {
            FileViewType.LIST ->
                ViewHolder(ItemVaultEntryListBinding.inflate(inflater, parent, false), viewType)
            FileViewType.GRID ->
                ViewHolder(ItemVaultEntryGridBinding.inflate(inflater, parent, false), viewType)
        }
        holder.itemLayout.apply {
            val isMaterial3Theme = context.isMaterial3Theme
            if (viewType == FileViewType.GRID && isMaterial3Theme) {
                foregroundCompat = context.getDrawableCompat(R.drawable.fg_file_item_grid_material3)
            }
            background = if (viewType == FileViewType.GRID && isMaterial3Theme) {
                CheckableItemBackground.create(4f, 12f, context)
            } else {
                CheckableItemBackground.create(0f, 0f, context)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        RecyclerItemAnimationHelper.clearAnimatedTag(holder.itemView)
    }

    inner class ViewHolder private constructor(
        root: View,
        val itemLayout: CheckableForegroundLinearLayout,
        private val iconImage: FileIconShapeView,
        private val nameText: TextView,
        private val descriptionText: TextView?,
        private val dateText: TextView?,
        private val itemViewType: FileViewType
    ) : RecyclerView.ViewHolder(root) {
        private val nameTextColors = nameText.textColors
        private val descriptionTextColors = descriptionText?.textColors
        private val dateTextColors = dateText?.textColors

        constructor(
            binding: ItemVaultEntryListBinding,
            viewType: FileViewType
        ) : this(
            binding.root,
            binding.itemLayout,
            binding.iconImage,
            binding.nameText,
            binding.descriptionText,
            binding.dateText,
            viewType
        )

        constructor(
            binding: ItemVaultEntryGridBinding,
            viewType: FileViewType
        ) : this(
            binding.root,
            binding.itemLayout,
            binding.iconImage,
            binding.nameText,
            null,
            null,
            viewType
        )

        fun bind(entry: VaultEntry) {
            val isSelected = entry.id in selectedEntryIds
            itemLayout.isChecked = isSelected
            itemLayout.isActivated = isSelected
            itemLayout.isSelected = isSelected
            val selectedContentColor = itemView.context.getColorByAttr(
                com.google.android.material.R.attr.colorOnSecondaryContainer
            )
            nameText.setTextColor(
                if (isSelected) ColorStateList.valueOf(selectedContentColor) else nameTextColors
            )
            descriptionText?.let { description ->
                descriptionTextColors?.let { defaultColors ->
                    description.setTextColor(
                        if (isSelected) {
                            ColorStateList.valueOf(selectedContentColor)
                        } else {
                            defaultColors
                        }
                    )
                }
            }
            dateText?.let { date ->
                dateTextColors?.let { defaultColors ->
                    date.setTextColor(
                        if (isSelected) {
                            ColorStateList.valueOf(selectedContentColor)
                        } else {
                            defaultColors
                        }
                    )
                }
            }
            RecyclerItemAnimationHelper.applySelectionScale(itemLayout, isSelected)

            nameText.text = entry.name
            descriptionText?.let { description ->
                val context = description.context
                description.text = if (entry.isDirectory) {
                    val itemCount = directoryItemCounts[entry.id]
                    when {
                        itemCount == null -> context.getString(R.string.file_type_name_directory)
                        itemCount == 0 -> context.getString(R.string.empty)
                        else -> context.resources.getQuantityString(
                            R.plurals.file_item_directory_count,
                            itemCount,
                            itemCount
                        )
                    }
                } else {
                    entry.size.asFileSize().formatHumanReadable(context)
                }
                dateText?.text = Instant.ofEpochMilli(entry.modifiedAt).formatShort(context)
            }
            val icon = if (entry.isDirectory) {
                if (itemViewType == FileViewType.GRID) {
                    R.drawable.ic_file_type_directory_thumbnail
                } else {
                    R.drawable.ic_file_type_directory
                }
            } else {
                MimeType.guessFromPath(entry.name).iconRes
            }
            iconImage.isVisible = true
            iconImage.setImageResource(icon)
            itemLayout.setOnClickListener { onClick(entry) }
            itemLayout.setOnLongClickListener {
                onLongClick(entry)
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<VaultEntry>() {
            override fun areItemsTheSame(oldItem: VaultEntry, newItem: VaultEntry): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: VaultEntry, newItem: VaultEntry): Boolean =
                oldItem == newItem
        }
    }
}
