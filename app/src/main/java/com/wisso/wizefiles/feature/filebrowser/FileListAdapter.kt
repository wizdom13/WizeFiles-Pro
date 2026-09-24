package com.wisso.wizefiles.feature.filebrowser

import android.content.res.ColorStateList
import android.os.Build
import android.text.TextUtils
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.imageloader.coil.AppIconPackageName
import com.wisso.wizefiles.core.android.compat.foregroundCompat
import com.wisso.wizefiles.core.android.compat.getDrawableCompat
import com.wisso.wizefiles.core.android.compat.isSingleLineCompat
import com.wisso.wizefiles.databinding.ItemFileGridBinding
import com.wisso.wizefiles.databinding.ItemFileListBinding
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.model.PendingFileOperationState
import com.wisso.wizefiles.core.files.extensions.fileSize
import com.wisso.wizefiles.core.files.extensions.formatShort
import com.wisso.wizefiles.core.files.mime.iconRes
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.ui.AnimatedListAdapter
import com.wisso.wizefiles.ui.CheckableForegroundLinearLayout
import com.wisso.wizefiles.ui.RecyclerItemAnimationHelper
import com.wisso.wizefiles.ui.CheckableItemBackground
import com.wisso.wizefiles.ui.FileIconShapeView
import com.wisso.wizefiles.util.isMaterial3Theme
import com.wisso.wizefiles.util.getColorByAttr
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.valueCompat
import java.util.Locale

class FileListAdapter(
    private val listener: Listener
) : AnimatedListAdapter<FileItem, FileListAdapter.ViewHolder>(CALLBACK), FastScrollLabelProvider {
    private var isSearching = false

    private lateinit var _viewType: FileViewType
    var viewType: FileViewType
        get() = _viewType
        set(value) {
            _viewType = value
            if (!isSearching) {
                super.replace(list, true)
            }
        }

    private lateinit var _sortOptions: FileSortOptions
    var sortOptions: FileSortOptions
        get() = _sortOptions
        set(value) {
            _sortOptions = value
            if (!isSearching) {
                val sortedList = list.sortedWith(value.createComparator())
                super.replace(sortedList, true)
                rebuildFilePositionMap()
            }
        }

    var pickOptions: PickOptions? = null
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    private val selectedFiles = fileItemSetOf()

    private val filePositionMap = mutableMapOf<AppPath, Int>()

    private lateinit var _nameEllipsize: TextUtils.TruncateAt
    var nameEllipsize: TextUtils.TruncateAt
        get() = _nameEllipsize
        set(value) {
            _nameEllipsize = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    fun replaceSelectedFiles(files: FileItemSet) {
        val changedFiles = fileItemSetOf()
        val iterator = selectedFiles.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file !in files) {
                iterator.remove()
                changedFiles.add(file)
            }
        }
        for (file in files) {
            if (file !in selectedFiles) {
                selectedFiles.add(file)
                changedFiles.add(file)
            }
        }
        for (file in changedFiles) {
            val position = filePositionMap[file.path]
            position?.let { notifyItemChanged(it, PAYLOAD_STATE_CHANGED) }
        }
    }

    private fun selectFile(file: FileItem) {
        selectFile(file, file !in selectedFiles)
    }

    private fun selectFile(file: FileItem, selected: Boolean) {
        if (!isFileSelectable(file)) {
            return
        }
        val pickOptions = pickOptions
        if (selected && pickOptions != null && !pickOptions.allowMultiple) {
            listener.clearSelectedFiles()
        }
        listener.selectFile(file, selected)
    }

    fun selectAllFiles() {
        val files = fileItemSetOf()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            if (isFileSelectable(file)) {
                files.add(file)
            }
        }
        listener.selectFiles(files, true)
    }

    private fun isFileSelectable(file: FileItem): Boolean {
        val isPendingUpload = file.pendingOperationState != null
        if (isPendingUpload) return false
        val pickOptions = pickOptions ?: return true
        return when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
                (!file.attributes.isDirectory &&
                    pickOptions.mimeTypes.any { it.match(file.mimeType) }) ||
                    (file.attributes.isDirectory &&
                        pickOptions.mode == PickOptions.Mode.OPEN_FILE &&
                        pickOptions.allowDirectories)
            PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
        }
    }

    override fun clear() {
        super.clear()

        rebuildFilePositionMap()
    }

    @Deprecated("", ReplaceWith("replaceListAndSearching(list, searching)"))
    override fun replace(list: List<FileItem>, clear: Boolean) {
        throw UnsupportedOperationException()
    }

    fun replaceListAndIsSearching(list: List<FileItem>, isSearching: Boolean) {
        val clear = this.isSearching != isSearching
        this.isSearching = isSearching
        val sortedList = if (!isSearching) list.sortedWith(sortOptions.createComparator()) else list
        super.replace(sortedList, clear)
        rebuildFilePositionMap()
    }


    fun findAdapterPosition(path: AppPath): Int = filePositionMap[path] ?: RecyclerView.NO_POSITION

    private fun rebuildFilePositionMap() {
        filePositionMap.clear()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            filePositionMap[file.path] = index
        }
    }

    private var gridLayoutGeneration = 0

    fun recreateGridViewHoldersForSpanChange() {
        if (viewType != FileViewType.GRID) {
            return
        }
        gridLayoutGeneration += 1
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val layoutGeneration = if (viewType == FileViewType.GRID) gridLayoutGeneration else 0
        return viewType.ordinal + layoutGeneration * FileViewType.entries.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewTypeCode: Int): ViewHolder {
        val viewType = FileViewType.entries[viewTypeCode % FileViewType.entries.size]
        val inflater = parent.context.layoutInflater
        val holder = when (viewType) {
            FileViewType.LIST -> ViewHolder(ItemFileListBinding.inflate(inflater, parent, false))
            FileViewType.GRID -> ViewHolder(ItemFileGridBinding.inflate(inflater, parent, false))
        }
        return holder.apply {
            itemLayout.apply {
                val context = context
                val isMaterial3Theme = context.isMaterial3Theme
                if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    foregroundCompat =
                        context.getDrawableCompat(R.drawable.fg_file_item_grid_material3)
                }
                background = if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    CheckableItemBackground.create(4f, 12f, context)
                } else {
                    CheckableItemBackground.create(0f, 0f, context)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        throw UnsupportedOperationException()
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.boundPath = null
        resetLoadedImages(holder)
        super.onViewRecycled(holder)
        RecyclerItemAnimationHelper.clearAnimatedTag(holder.itemView)
    }

    private fun resetLoadedImages(holder: ViewHolder) {
        holder.iconImage.apply {
            dispose()
            showBuiltInIcon()
            setImageDrawable(null)
            isVisible = false
        }
        holder.directoryThumbnailImage?.apply {
            dispose()
            showBuiltInIcon()
            setImageDrawable(null)
            isVisible = false
        }
        holder.thumbnailIconImage
            ?.takeIf { it !== holder.iconImage }
            ?.apply {
                dispose()
                showBuiltInIcon()
                setImageDrawable(null)
                isVisible = false
            }
        holder.thumbnailImage.apply {
            dispose()
            showThumbnail()
            setImageDrawable(null)
            isVisible = false
        }
        holder.thumbnailOutlineView?.apply {
            setImageDrawable(null)
            isVisible = false
        }
        holder.appIconBadgeImage.apply {
            dispose()
            setImageDrawable(null)
            isVisible = false
        }
        holder.badgeImage.apply {
            setImageDrawable(null)
            isVisible = false
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        val file = getItem(position)
        val isDirectory = file.attributes.isDirectory
        val path = file.path
        holder.boundPath = path
        val legacyPath = runCatching { path.toLegacyPathOrNull() }.getOrNull()
        val uploadState = file.pendingOperationState
        val isPendingUpload = uploadState != null
        val isEnabled = !isPendingUpload && (isFileSelectable(file) || isDirectory)
        holder.itemLayout.isEnabled = isEnabled
        val checked = file in selectedFiles
        holder.itemLayout.isChecked = checked
        holder.itemLayout.isActivated = checked
        holder.itemLayout.isSelected = checked
        val selectedContentColor = holder.itemView.context.getColorByAttr(
            com.google.android.material.R.attr.colorOnSecondaryContainer
        )
        holder.nameText.setTextColor(
            if (checked) ColorStateList.valueOf(selectedContentColor) else holder.nameTextColors
        )
        holder.descriptionText?.let { descriptionText ->
            holder.descriptionTextColors?.let { defaultColors ->
                descriptionText.setTextColor(
                    if (checked) ColorStateList.valueOf(selectedContentColor) else defaultColors
                )
            }
        }
        holder.dateText?.let { dateText ->
            holder.dateTextColors?.let { defaultColors ->
                dateText.setTextColor(
                    if (checked) ColorStateList.valueOf(selectedContentColor) else defaultColors
                )
            }
        }
        RecyclerItemAnimationHelper.applySelectionScale(holder.itemLayout, checked)
        RecyclerItemAnimationHelper.assignTransitionName(holder.thumbnailImage, file.path.toString().hashCode().toString())
        holder.nameText.apply {
            if (isSingleLineCompat) {
                val nameEllipsize = nameEllipsize
                ellipsize = nameEllipsize
                isSelected = nameEllipsize == TextUtils.TruncateAt.MARQUEE
            }
        }
        if (payloads.isNotEmpty()) {
            return
        }
        resetLoadedImages(holder)
        bindViewHolderAnimation(holder)
        val dragSlop = ViewConfiguration.get(holder.itemView.context).scaledTouchSlop
        var dragArmed = false
        var downX = 0f
        var downY = 0f
        holder.itemLayout.apply {
            setOnClickListener {
                onItemClick(file, pickOptions)
            }
            setOnLongClickListener {
                val options = pickOptions
                if (options != null) {
                    if (options.selectWithLongPress || selectedFiles.isEmpty()) {
                        selectFile(file)
                    } else {
                        listener.openFile(file)
                    }
                } else {
                    if (file !in selectedFiles) {
                        listener.clearSelectedFiles()
                        selectFile(file, true)
                    }
                    dragArmed = true
                }
                true
            }
            setOnTouchListener { target, event ->
                val isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val crossedSlop =
                            kotlin.math.abs(event.x - downX) >= dragSlop ||
                                kotlin.math.abs(event.y - downY) >= dragSlop
                        when {
                            isMouse &&
                                (event.buttonState and MotionEvent.BUTTON_PRIMARY) != 0 &&
                                crossedSlop &&
                                pickOptions == null -> {
                                if (file !in selectedFiles) {
                                    listener.clearSelectedFiles()
                                    selectFile(file, true)
                                }
                                dragArmed = false
                                listener.startDrag(file, target, BrowserDragInput.MOUSE)
                            }
                            !isMouse && dragArmed && crossedSlop -> {
                                dragArmed = false
                                listener.startDrag(file, target, BrowserDragInput.TOUCH)
                            }
                            else -> false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        dragArmed = false
                        false
                    }
                    else -> false
                }
            }
            setOnDragListener(
                if (isDirectory) {
                    View.OnDragListener { target, event ->
                        listener.onFileDragEvent(file, target, event)
                    }
                } else {
                    null
                }
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            holder.itemLayout.setOnContextClickListener { target ->
                listener.showContextMenu(file, target)
            }
        }
        holder.itemLayout.setOnGenericMotionListener { target, event ->
            if (
                event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
                event.actionButton == MotionEvent.BUTTON_SECONDARY
            ) {
                listener.showContextMenu(file, target)
            } else {
                false
            }
        }
        val iconRes = file.mimeType.iconRes
        holder.iconImage.apply {
            showBuiltInIcon()
            isVisible = true
            setImageResource(iconRes)
        }
        holder.directoryThumbnailImage?.apply {
            showBuiltInIcon()
            setImageResource(R.drawable.ic_file_type_directory_thumbnail)
            isVisible = isDirectory
        }
        val supportsThumbnail = !isDirectory && file.supportsThumbnail
        val shouldLoadThumbnailIcon = supportsThumbnail && holder.thumbnailIconImage != null &&
            file.mimeType.isApk
        val shouldLoadThumbnail = supportsThumbnail && !shouldLoadThumbnailIcon
        holder.thumbnailOutlineView?.isVisible = shouldLoadThumbnail
        val attributes = file.attributes
        holder.thumbnailIconImage?.apply {
            dispose()
            showBuiltInIcon()
            isVisible = !isDirectory
            setImageResource(iconRes)
            if (shouldLoadThumbnailIcon) {
                load(path to attributes) {
                    error(iconRes)
                    listener(
                        onError = { _, _ ->
                            if (holder.boundPath == path) {
                                showBuiltInIcon()
                            }
                        },
                        onSuccess = { _, _ ->
                            if (holder.boundPath == path) {
                                showAppIcon()
                            }
                        }
                    )
                }
            }
        }
        holder.thumbnailImage.apply {
            dispose()
            showThumbnail()
            setImageDrawable(null)
            isVisible = shouldLoadThumbnail
            if (shouldLoadThumbnail) {
                load(path to attributes) {
                    listener(
                        onSuccess = { _, _ ->
                            if (holder.boundPath == path) {
                                if (file.mimeType.isApk) {
                                    showAppIcon()
                                } else {
                                    showThumbnail()
                                }
                                val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                                iconImage.isVisible = false
                            }
                        }
                    )
                }
            }
        }
        holder.appIconBadgeImage.apply {
            dispose()
            setImageDrawable(null)
            val appDirectoryPackageName = file.appDirectoryPackageName
            val hasAppIconBadge = appDirectoryPackageName != null
            isVisible = hasAppIconBadge
            if (hasAppIconBadge) {
                load(AppIconPackageName(appDirectoryPackageName!!))
            }
        }
        holder.badgeImage.apply {
            val badgeIconRes = if (isPendingUpload) {
                R.drawable.ic_badge_upload_18dp
            } else if (file.attributesNoFollowLinks.isSymbolicLink) {
                if (file.isSymbolicLinkBroken) {
                    R.drawable.ic_badge_error_18dp
                } else {
                    R.drawable.ic_badge_symbolic_link_18dp
                }
            } else {
                null
            }
            val hasBadge = badgeIconRes != null
            isVisible = hasBadge
            if (hasBadge) {
                setImageResource(badgeIconRes!!)
            } else {
                setImageDrawable(null)
            }
        }
        holder.nameText.text = file.name
        holder.descriptionText?.let { descriptionText ->
            val context = descriptionText.context
            val descriptionSeparator = context.getString(R.string.file_item_description_separator)
            val directoryDescription = file.directoryItemCount?.let { itemCount ->
                if (itemCount == 0) {
                    context.getString(R.string.empty)
                } else {
                    context.resources.getQuantityString(
                        R.plurals.file_item_directory_count,
                        itemCount,
                        itemCount
                    )
                }
            } ?: context.getString(R.string.file_type_name_directory)
            val itemDescription = when {
                isPendingUpload -> {
                    val pendingUploadState = requireNotNull(uploadState)
                    listOf(
                        context.getString(
                            when (pendingUploadState) {
                                PendingFileOperationState.QUEUED ->
                                    R.string.transfer_section_queued
                                PendingFileOperationState.COPYING ->
                                    R.string.transfer_action_copying
                            }
                        ),
                        file.attributes.fileSize.formatHumanReadable(context)
                    ).joinToString(descriptionSeparator)
                }
                isDirectory -> directoryDescription
                else -> attributes.fileSize.formatHumanReadable(context)
            }
            val parentPath = if (isSearching) {
                legacyPath?.parent?.toUserFriendlyString()
            } else {
                null
            }
            descriptionText.text = listOfNotNull(parentPath, itemDescription)
                .joinToString(descriptionSeparator)
            holder.dateText?.text = attributes.lastModifiedTime().toInstant().formatShort(context)
        }
    }

    override fun getFastScrollPopupText(position: Int): String? {
        val file = getItem(position)
        return when (sortOptions.by) {
            FileSortOptions.By.NAME -> file.name.take(1).uppercase(Locale.getDefault())
            FileSortOptions.By.TYPE -> file.extension.uppercase(Locale.getDefault())
            FileSortOptions.By.SIZE -> file.attributes.fileSize.toString()
            FileSortOptions.By.LAST_MODIFIED -> file.attributes.lastModifiedTime().toInstant().toString()
        }
    }

    override val isAnimationEnabled: Boolean
        get() = Settings.FILE_LIST_ANIMATION.valueCompat

    private fun onItemClick(file: FileItem, pickOptions: PickOptions?) {
        if (shouldToggleSelectionOnClick(selectedFiles.isNotEmpty(), pickOptions)) {
            selectFile(file)
        } else {
            listener.openFile(file)
        }
    }

    companion object {
        internal fun shouldToggleSelectionOnClick(
            hasSelectedFiles: Boolean,
            pickOptions: PickOptions?
        ): Boolean {
            if (pickOptions?.selectWithLongPress == true) {
                return false
            }
            return hasSelectedFiles || pickOptions?.allowMultiple == true
        }

        private val PAYLOAD_STATE_CHANGED = Any()

        private val CALLBACK = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem == newItem
        }
    }

    class ViewHolder private constructor(
        root: View,
        val itemLayout: CheckableForegroundLinearLayout,
        val iconLayout: View,
        val iconImage: FileIconShapeView,
        val directoryThumbnailImage: FileIconShapeView?,
        val thumbnailOutlineView: FileIconShapeView?,
        val thumbnailIconImage: FileIconShapeView?,
        val thumbnailImage: FileIconShapeView,
        val appIconBadgeImage: ImageView,
        val badgeImage: ImageView,
        val nameText: TextView,
        val descriptionText: TextView?,
        val dateText: TextView?
    ) : RecyclerView.ViewHolder(root) {
        val nameTextColors: ColorStateList = nameText.textColors
        val descriptionTextColors: ColorStateList? = descriptionText?.textColors
        val dateTextColors: ColorStateList? = dateText?.textColors
        var boundPath: AppPath? = null

        constructor(binding: ItemFileListBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            null,
            null,
            null,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            binding.descriptionText,
            binding.dateText
        )

        constructor(binding: ItemFileGridBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.thumbnailIconImage,
            binding.directoryThumbnailImage,
            binding.thumbnailOutlineView,
            binding.thumbnailIconImage,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            null,
            null
        )
    }

    interface Listener {
        fun clearSelectedFiles()
        fun selectFile(file: FileItem, selected: Boolean)
        fun selectFiles(files: FileItemSet, selected: Boolean)
        fun showContextMenu(file: FileItem, anchor: View): Boolean
        fun startDrag(file: FileItem, anchor: View, input: BrowserDragInput): Boolean
        fun onFileDragEvent(file: FileItem, target: View, event: android.view.DragEvent): Boolean
        fun openFile(file: FileItem)
    }
}
