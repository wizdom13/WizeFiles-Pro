package com.wisso.wizefiles.feature.filebrowser

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filejobs.DeleteOptions
import com.wisso.wizefiles.feature.filejobs.DeleteOptionsDialog
import com.wisso.wizefiles.feature.filejobs.DeleteTargetMode
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.getQuantityString
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.show

class ConfirmDeleteFilesDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val files = args.files
        val message = if (files.size == 1) {
            val file = files.single()
            val messageRes = if (file.attributesNoFollowLinks.isDirectory) {
                R.string.file_delete_message_directory_format
            } else {
                R.string.file_delete_message_file_format
            }
            getString(messageRes, file.name)
        } else {
            val allDirectories = files.all { it.attributesNoFollowLinks.isDirectory }
            val allFiles = files.none { it.attributesNoFollowLinks.isDirectory }
            val messageRes = when {
                allDirectories -> R.plurals.file_delete_message_multiple_directories_format
                allFiles -> R.plurals.file_delete_message_multiple_files_format
                else -> R.plurals.file_delete_message_multiple_mixed_format
            }
            getQuantityString(messageRes, files.size, files.size)
        }
        val messageWithWarning = when (args.deleteTargetMode) {
            DeleteTargetMode.PERMANENT_ONLY ->
                "$message\n\n${getString(R.string.delete_option_remote_permanent_warning)}"
            DeleteTargetMode.MIXED ->
                "$message\n\n${getString(R.string.delete_option_mixed_storage_warning)}"
            else -> message
        }
        val localTrashOptions = args.deleteTargetMode == DeleteTargetMode.LOCAL_TRASH
        return DeleteOptionsDialog.create(
            context = requireContext(),
            message = messageWithWarning,
            supportsSecureShred = args.supportsSecureShred,
            initialOptions = args.initialOptions,
            uiConfig = DeleteOptionsDialog.UiConfig(
                permanentDeleteChecked = if (localTrashOptions) null else false,
                permanentDeleteEnabled = localTrashOptions,
                permanentDeleteVisible = localTrashOptions,
                secureShredVisible = localTrashOptions
            )
        ) { options ->
            listener.deleteFiles(files, options)
        }
    }

    companion object {
        fun show(
            files: FileItemSet,
            deleteTargetMode: DeleteTargetMode,
            supportsSecureShred: Boolean,
            initialOptions: DeleteOptions,
            fragment: Fragment
        ) {
            ConfirmDeleteFilesDialogFragment()
                .putArgs(
                    Args(files, deleteTargetMode, supportsSecureShred, initialOptions)
                )
                .show(fragment)
        }
    }

    @Parcelize
    class Args(
        val files: FileItemSet,
        val deleteTargetMode: DeleteTargetMode,
        val supportsSecureShred: Boolean,
        val initialOptions: DeleteOptions
    ) : ParcelableArgs

    interface Listener {
        fun deleteFiles(files: FileItemSet, options: DeleteOptions)
    }
}
