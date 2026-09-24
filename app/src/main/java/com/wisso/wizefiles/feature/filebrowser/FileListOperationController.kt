package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filejobs.DeleteConfirmationSessionStore
import com.wisso.wizefiles.feature.filejobs.DeleteOptions
import com.wisso.wizefiles.feature.filejobs.DeleteOptionsSupport
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.filejobs.DeleteTargetMode
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.archive.editor.ArchiveConflictPolicy
import com.wisso.wizefiles.provider.archive.editor.ArchiveEditCapabilities
import com.wisso.wizefiles.recyclebin.RecycleBinManager
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.VaporizeDeleteAnimator
import com.wisso.wizefiles.util.showToast
import java.nio.file.Path

internal class FileListOperationController(
    private val viewModel: FileListViewModel
) {
    private var context: Context? = null
    private var adapter: FileListAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var refresh: (() -> Unit)? = null

    fun bind(
        context: Context,
        adapter: FileListAdapter,
        recyclerView: RecyclerView,
        refresh: () -> Unit
    ) {
        release()
        this.context = context
        this.adapter = adapter
        this.recyclerView = recyclerView
        this.refresh = refresh
    }

    fun release() {
        refresh = null
        recyclerView = null
        adapter = null
        context = null
    }

    fun cut(files: FileItemSet) {
        viewModel.addToPasteState(false, files)
        viewModel.selectFiles(files, false)
    }

    fun copy(files: FileItemSet) {
        viewModel.addToPasteState(true, files)
        viewModel.selectFiles(files, false)
    }

    fun confirmDelete(
        files: FileItemSet,
        showConfirmation: (
            deleteTargetMode: DeleteTargetMode,
            supportsSecureShred: Boolean,
            initialOptions: DeleteOptions
        ) -> Unit
    ) {
        val context = requireNotNull(context)
        val paths = fileItemPathsForJob(files)
        val deleteTargetMode = DeleteOptionsSupport.targetMode(paths)
        val supportsPermanentDelete =
            deleteTargetMode == DeleteTargetMode.LOCAL_TRASH
        val supportsSecureShred = DeleteOptionsSupport.supportsSecureShred(paths)
        val rememberedOptions = DeleteConfirmationSessionStore.get(deleteTargetMode)
        val supportedRememberedOptions = rememberedOptions?.let { options ->
            if (options.permanentDelete && !supportsPermanentDelete) {
                options.copy(permanentDelete = false)
            } else {
                options
            }
        }
        if (supportedRememberedOptions?.secureShred == true && !supportsSecureShred) {
            context.showToast(R.string.delete_option_secure_shred_session_fallback_message)
        }
        if (
            supportedRememberedOptions != null &&
            (!supportedRememberedOptions.secureShred || supportsSecureShred)
        ) {
            delete(files, supportedRememberedOptions)
            return
        }
        showConfirmation(
            deleteTargetMode,
            supportsSecureShred,
            supportedRememberedOptions ?: DeleteOptions()
        )
    }

    fun delete(files: FileItemSet, options: DeleteOptions) {
        val context = requireNotNull(context)
        val paths = fileItemPathsForJob(files)
        DeleteConfirmationSessionStore.update(
            DeleteOptionsSupport.targetMode(paths),
            options.toRuntimeOptions()
        )
        if (paths.isNotEmpty() && paths.all { it.isArchivePath }) {
            FileOperationService.delete(paths, context, options)
            viewModel.selectFiles(files, false)
            return
        }
        val adapter = requireNotNull(adapter)
        val recyclerView = requireNotNull(recyclerView)
        val positions = files.mapNotNull { file ->
            adapter.findAdapterPosition(file.path).takeIf { it != RecyclerView.NO_POSITION }
        }.toSet()
        VaporizeDeleteAnimator.animatePositions(recyclerView, positions) {
            FileOperationService.delete(paths, context, options)
            viewModel.selectFiles(files, false)
        }
    }

    fun restore(files: FileItemSet) {
        val context = requireNotNull(context)
        Thread {
            var restoredCount = 0
            var failureCount = 0
            files.forEach {
                runCatching {
                    it.path.toLegacyPathOrNull()?.let(RecycleBinManager::restore)
                }.onSuccess {
                    restoredCount++
                }.onFailure {
                    failureCount++
                }
            }
            recyclerView?.post {
                if (restoredCount > 0) {
                    context.showToast(
                        context.getString(R.string.file_list_recycle_bin_restore_success, restoredCount)
                    )
                }
                if (failureCount > 0) {
                    context.showToast(
                        context.getString(R.string.file_list_recycle_bin_partial_failure, failureCount)
                    )
                }
                viewModel.selectFiles(files, false)
                refresh?.invoke()
            }
        }.start()
    }

    fun restoreAll() {
        val context = requireNotNull(context)
        Thread {
            val summary = RecycleBinManager.restoreAll()
            recyclerView?.post {
                context.showToast(
                    context.getString(
                        R.string.file_list_recycle_bin_restore_success,
                        summary.successCount
                    )
                )
                if (summary.hasFailures) {
                    context.showToast(
                        context.getString(
                            R.string.file_list_recycle_bin_partial_failure,
                            summary.failures.size
                        )
                    )
                }
                refresh?.invoke()
            }
        }.start()
    }

    fun confirmDeleteAll() {
        val context = requireNotNull(context)
        MaterialAlertDialogBuilder(context)
            .setMessage(R.string.file_list_recycle_bin_delete_all_confirmation)
            .setPositiveButton(android.R.string.ok) { _, _ -> deleteAll() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun extract(files: FileItemSet) {
        copy(files.mapTo(fileItemSetOf()) { it.createDummyArchiveRoot() })
        viewModel.selectFiles(files, false)
    }

    fun archive(
        files: FileItemSet,
        name: String,
        format: Int,
        filter: Int,
        password: String?
    ) {
        val archiveFile = viewModel.currentPath.resolve(name)
        FileOperationService.archive(
            fileItemPathsForJob(files),
            archiveFile,
            format,
            filter,
            password,
            requireNotNull(context)
        )
        viewModel.selectFiles(files, false)
    }

    fun encrypt(files: FileItemSet, password: CharArray, algorithmId: Int, kdfId: Int) {
        FileOperationService.encrypt(
            fileItemPathsForJob(files),
            password,
            algorithmId,
            kdfId,
            requireNotNull(context)
        )
        viewModel.selectFiles(files, false)
    }

    fun decrypt(files: FileItemSet, password: CharArray) {
        FileOperationService.decrypt(
            fileItemPathsForJob(files),
            password,
            requireNotNull(context)
        )
        viewModel.selectFiles(files, false)
    }

    fun paste(targetDirectory: Path) {
        val context = requireNotNull(context)
        val pasteState = viewModel.pasteState
        val sources = fileItemPathsForJob(pasteState.files)
        val start: (ArchiveConflictPolicy) -> Unit = { policy ->
            if (pasteState.copy) {
                FileOperationService.copy(sources, targetDirectory, context, policy)
            } else {
                FileOperationService.move(sources, targetDirectory, context, policy)
            }
            viewModel.clearPasteState()
        }
        if (!ArchiveEditCapabilities.isEditableLocation(targetDirectory)) {
            start(ArchiveConflictPolicy.FAIL)
            return
        }
        val existingNames = (viewModel.fileListStateful as? Success)?.value
            ?.mapTo(mutableSetOf()) { it.name.lowercase() }.orEmpty()
        val hasConflict = sources.any { source ->
            source.fileName?.toString()?.lowercase() in existingNames
        }
        if (!hasConflict) {
            start(ArchiveConflictPolicy.FAIL)
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.archive_edit_conflict_title)
            .setItems(
                arrayOf(
                    context.getString(R.string.archive_edit_conflict_replace),
                    context.getString(R.string.archive_edit_conflict_keep_both),
                    context.getString(R.string.archive_edit_conflict_skip)
                )
            ) { _, which ->
                start(
                    when (which) {
                        0 -> ArchiveConflictPolicy.REPLACE
                        1 -> ArchiveConflictPolicy.KEEP_BOTH
                        else -> ArchiveConflictPolicy.SKIP
                    }
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteAll() {
        val context = requireNotNull(context)
        Thread {
            val summary = RecycleBinManager.deleteAllPermanently()
            recyclerView?.post {
                context.showToast(
                    context.getString(
                        R.string.file_list_recycle_bin_delete_all_success,
                        summary.successCount
                    )
                )
                if (summary.hasFailures) {
                    context.showToast(
                        context.getString(
                            R.string.file_list_recycle_bin_partial_failure,
                            summary.failures.size
                        )
                    )
                }
                refresh?.invoke()
            }
        }.start()
    }
}

internal fun fileItemPathsForJob(files: FileItemSet): List<Path> =
    files.mapNotNull { it.path.toLegacyPathOrNull() }.sortedBy { it.toUri() }
