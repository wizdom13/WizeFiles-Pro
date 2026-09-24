// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcel
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import coil.dispose
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.imageloader.coil.AppIconPackageName
import com.wisso.wizefiles.core.android.compat.requireViewByIdCompat
import com.wisso.wizefiles.databinding.DialogFileJobConflictBinding
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.extensions.fileSize
import com.wisso.wizefiles.core.files.extensions.formatShort
import com.wisso.wizefiles.core.files.mime.iconRes
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.core.files.extensions.lastModifiedInstant
import com.wisso.wizefiles.feature.filebrowser.appDirectoryPackageName
import com.wisso.wizefiles.feature.filebrowser.supportsThumbnail
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.ui.FileIconShapeView
import com.wisso.wizefiles.util.asPathNameOrNull
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.ParcelableState
import com.wisso.wizefiles.util.RemoteCallback
import com.wisso.wizefiles.util.applyInsetPadding
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.getArgs
import com.wisso.wizefiles.util.getState
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.putState
import com.wisso.wizefiles.util.readParcelable
import com.wisso.wizefiles.util.setTextWithSelection
import com.wisso.wizefiles.util.shortAnimTime
import com.wisso.wizefiles.util.showSoftInput

class FileOperationConflictDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: DialogFileJobConflictBinding

    private var isListenerNotified = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(State(binding.allCheck.isChecked))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val sourceFile = args.sourceFile
        val targetFile = args.targetFile
        val title = getTitle(sourceFile, targetFile, requireContext())
        val message = getMessage(sourceFile, targetFile, args.type, requireContext())
        val isMerge = isMerge(sourceFile, targetFile)
        val positiveButtonRes = if (isMerge) R.string.merge else R.string.replace
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(title)
            .setMessage(message)
            .apply {
                binding = DialogFileJobConflictBinding.inflate(context.layoutInflater)
                binding.targetNameText.setText(
                    if (isMerge) {
                        R.string.file_job_merge_target_name
                    } else {
                        R.string.file_job_replace_target_name
                    }
                )
                bindFileItem(
                    targetFile, binding.targetIconImage, binding.targetThumbnailImage,
                    binding.targetAppIconBadgeImage, binding.targetBadgeImage,
                    binding.targetDescriptionText
                )
                binding.sourceNameText.setText(
                    if (isMerge) {
                        R.string.file_job_merge_source_name
                    } else {
                        R.string.file_job_replace_source_name
                    }
                )
                bindFileItem(
                    sourceFile, binding.sourceIconImage, binding.sourceThumbnailImage,
                    binding.sourceAppIconBadgeImage, binding.sourceBadgeImage,
                    binding.sourceDescriptionText
                )
                binding.showNameLayout.setOnClickListener {
                    val visible = !binding.nameLayout.isVisible
                    binding.showNameArrowImage.animate()
                        .rotation(if (visible) 90f else 0f)
                        .setDuration(shortAnimTime.toLong())
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                    binding.nameLayout.isVisible = visible
                    if (visible) {
                        binding.nameEdit.requestFocus()
                        binding.nameEdit.showSoftInput()
                    }
                }
                val targetFileName = targetFile.path.name
                binding.nameEdit.setTextWithSelection(targetFileName)
                binding.nameEdit.doAfterTextChanged {
                    val hasNewName = hasNewName()
                    binding.allCheck.isEnabled = !hasNewName
                    if (hasNewName) {
                        binding.allCheck.isChecked = false
                    }
                    val positiveButton = requireDialog()
                        .requireViewByIdCompat<Button>(android.R.id.button1)
                    positiveButton.setText(if (hasNewName) R.string.rename else positiveButtonRes)
                }
                binding.nameLayout.setEndIconOnClickListener {
                    binding.nameEdit.setTextWithSelection(targetFileName)
                }
                if (savedInstanceState != null) {
                    binding.allCheck.isChecked = savedInstanceState.getState<State>().isAllChecked
                }
            }
            .setPositiveButton(positiveButtonRes, ::onDialogButtonClick)
            .setNegativeButton(R.string.skip, ::onDialogButtonClick)
            .setNeutralButton(android.R.string.cancel, ::onDialogButtonClick)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
            }
    }

    /** @see com.wisso.wizefiles.feature.filebrowser.FileListAdapter.onBindViewHolder */
    private fun bindFileItem(
        file: FileItem,
        iconImage: FileIconShapeView,
        thumbnailImage: FileIconShapeView,
        appIconBadgeImage: ImageView,
        badgeImage: ImageView,
        descriptionText: TextView
    ) {
        val path = file.path
        iconImage.apply {
            showBuiltInIcon()
            isVisible = true
            setImageResource(file.mimeType.iconRes)
        }
        val attributes = file.attributes
        thumbnailImage.apply {
            dispose()
            showThumbnail()
            setImageDrawable(null)
            val supportsThumbnail = file.supportsThumbnail
            isVisible = supportsThumbnail
            if (supportsThumbnail) {
                load(path to attributes) {
                    listener(
                        onSuccess = { _, _ ->
                            if (file.mimeType.isApk) {
                                showAppIcon()
                            } else {
                                showThumbnail()
                            }
                            iconImage.isVisible = false
                        }
                    )
                }
            }
        }
        appIconBadgeImage.apply {
            dispose()
            setImageDrawable(null)
            val appDirectoryPackageName = file.appDirectoryPackageName
            val hasAppIconBadge = appDirectoryPackageName != null
            isVisible = hasAppIconBadge
            if (hasAppIconBadge) {
                load(AppIconPackageName(appDirectoryPackageName!!))
            }
        }
        badgeImage.apply {
            val badgeIconRes = if (file.attributesNoFollowLinks.isSymbolicLink) {
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
        val lastModificationTime = attributes.lastModifiedInstant
            .formatShort(descriptionText.context)
        val size = attributes.fileSize.formatHumanReadable(descriptionText.context)
        val descriptionSeparator = getString(R.string.file_item_description_separator)
        descriptionText.text = listOf(lastModificationTime, size).joinToString(descriptionSeparator)
    }

    private fun onDialogButtonClick(dialog: DialogInterface, which: Int) {
        val action: FileOperationConflictAction
        val name: String?
        val all: Boolean
        when (which) {
            DialogInterface.BUTTON_POSITIVE ->
                if (hasNewName()) {
                    action = FileOperationConflictAction.RENAME
                    name = binding.nameEdit.text.toString()
                    all = false
                } else {
                    action = FileOperationConflictAction.MERGE_OR_REPLACE
                    name = null
                    all = binding.allCheck.isChecked
                }
            DialogInterface.BUTTON_NEGATIVE -> {
                action = FileOperationConflictAction.SKIP
                name = null
                all = binding.allCheck.isChecked
            }
            DialogInterface.BUTTON_NEUTRAL -> {
                action = FileOperationConflictAction.CANCEL
                name = null
                all = false
            }
            else -> throw AssertionError(which)
        }
        notifyListenerOnce(action, name, all)
        finish()
    }

    private fun hasNewName(): Boolean {
        val name = binding.nameEdit.text.toString()
        if (name.isEmpty()) {
            return false
        }
        val fileName = args.targetFile.path.name
        return name != fileName
    }

    override fun onStart() {
        super.onStart()

        if (binding.root.parent == null) {
            val dialog = requireDialog() as AlertDialog
            dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            val scrollView = dialog.requireViewByIdCompat<NestedScrollView>(R.id.scrollView)
            scrollView.applyInsetPadding(applyBottom = true, applyImeBottom = true)
            val linearLayout = scrollView.getChildAt(0) as LinearLayout
            linearLayout.addView(binding.root)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        notifyListenerOnce(FileOperationConflictAction.CANCELED, null, false)
        finish()
    }

    fun onFinish() {
        notifyListenerOnce(FileOperationConflictAction.CANCELED, null, false)
    }

    private fun notifyListenerOnce(action: FileOperationConflictAction, name: String?, isAll: Boolean) {
        if (isListenerNotified) {
            return
        }
        args.listener(action, name, isAll)
        isListenerNotified = true
    }

    companion object {
        fun getTitle(sourceFile: FileItem, targetFile: FileItem, context: Context): String {
            val titleRes = if (isMerge(sourceFile, targetFile)) {
                R.string.file_job_merge_title_format
            } else {
                R.string.file_job_replace_title_format
            }
            return context.getString(titleRes, targetFile.path.name)
        }

        fun getMessage(
            sourceFile: FileItem,
            targetFile: FileItem,
            type: CopyMoveType,
            context: Context
        ): String {
            val messageRes = if (isMerge(sourceFile, targetFile)) {
                type.getResourceId(
                    R.string.file_job_merge_copy_message_format,
                    R.string.file_job_merge_extract_message_format,
                    R.string.file_job_merge_move_message_format
                )
            } else {
                R.string.file_job_replace_message_format
            }
            return context.getString(messageRes, targetFile.path.parentDisplayName)
        }

        private fun isMerge(sourceFile: FileItem, targetFile: FileItem): Boolean {
            val sourceIsDirectory = sourceFile.attributesNoFollowLinks.isDirectory
            val targetIsDirectory = targetFile.attributesNoFollowLinks.isDirectory
            return sourceIsDirectory && targetIsDirectory
        }

        private val AppPath.parentDisplayName: String
            get() {
                val parentPath = rawPath.asPathNameOrNull()?.directoryName ?: return name
                return parentPath.asPathNameOrNull()?.fileName ?: name
            }
    }

    @Parcelize
    class Args(
        val sourceFile: FileItem,
        val targetFile: FileItem,
        val type: CopyMoveType,
        val listener: @WriteWith<ListenerParceler>()
        (FileOperationConflictAction, String?, Boolean) -> Unit
    ) : ParcelableArgs {
        object ListenerParceler : Parceler<(FileOperationConflictAction, String?, Boolean) -> Unit> {
            override fun create(parcel: Parcel): (FileOperationConflictAction, String?, Boolean) -> Unit =
                parcel.readParcelable<RemoteCallback>()!!.let {
                    { action, name, isAll ->
                        it.sendResult(Bundle().putArgs(ListenerArgs(action, name, isAll)))
                    }
                }

            override fun ((FileOperationConflictAction, String?, Boolean) -> Unit).write(
                parcel: Parcel,
                flags: Int
            ) {
                parcel.writeParcelable(
                    RemoteCallback {
                        val args = it.getArgs<ListenerArgs>()
                        this(args.action, args.name, args.isAll)
                    }, flags
                )
            }

            @Parcelize
            private class ListenerArgs(
                val action: FileOperationConflictAction,
                val name: String?,
                val isAll: Boolean
            ) : ParcelableArgs
        }
    }

    @Parcelize
    private class State(
        val isAllChecked: Boolean
    ) : ParcelableState
}
