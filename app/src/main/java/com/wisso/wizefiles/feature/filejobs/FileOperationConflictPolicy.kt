package com.wisso.wizefiles.feature.filejobs

import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BackgroundActivityStarter
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.model.loadFileItem
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs
import kotlin.coroutines.resume

internal data class FileOperationConflictDialogResult(
    val action: FileOperationConflictAction,
    val name: String?,
    val isAll: Boolean
)

internal class FileOperationActionAllInfo(
    var skipCopyMoveIntoItself: Boolean = false,
    var skipCopyMoveOverItself: Boolean = false,
    var merge: Boolean = false,
    var replace: Boolean = false,
    var skipMerge: Boolean = false,
    var skipReplace: Boolean = false,
    var skipCopyMoveError: Boolean = false,
    var skipDeleteError: Boolean = false,
    var skipRestoreSeLinuxContextError: Boolean = false,
    var skipSetGroupError: Boolean = false,
    var skipSetOwnerError: Boolean = false,
    var skipSetModeError: Boolean = false,
    var skipSetSeLinuxContextError: Boolean = false
)

internal enum class FileOperationStructuralConflictKind {
    INTO_SELF,
    OVER_SELF
}

internal sealed class FileOperationStructuralConflictResolution {
    object Continue : FileOperationStructuralConflictResolution()
    object Skip : FileOperationStructuralConflictResolution()
    object Interrupt : FileOperationStructuralConflictResolution()
}

internal sealed class FileOperationTargetConflictResolution {
    object MergeIntoDirectory : FileOperationTargetConflictResolution()
    object RetryWithReplace : FileOperationTargetConflictResolution()
    data class RetryWithRename(val target: Path) : FileOperationTargetConflictResolution()
    object Skip : FileOperationTargetConflictResolution()
    object Interrupt : FileOperationTargetConflictResolution()
}

internal fun canReplaceTarget(sourceIsDirectory: Boolean, targetIsDirectory: Boolean): Boolean =
    sourceIsDirectory == targetIsDirectory

@Throws(FileAlreadyExistsException::class)
internal fun requireCompatibleReplacement(
    source: Path,
    target: Path,
    exception: FileAlreadyExistsException,
    isDirectory: (Path) -> Boolean
) {
    if (!canReplaceTarget(isDirectory(source), isDirectory(target))) throw exception
}

@Throws(IOException::class)
internal fun FileOperationJob.showConflictDialog(
    sourceFile: FileItem,
    targetFile: FileItem,
    type: CopyMoveType
): FileOperationConflictDialogResult =
    try {
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                BackgroundActivityStarter.startActivity(
                    FileOperationConflictDialogHostActivity::class.createIntent().putArgs(
                        FileOperationConflictDialogFragment.Args(
                            sourceFile, targetFile, type
                        ) { action, name, all ->
                            continuation.resume(
                                FileOperationConflictDialogResult(action, name, all)
                            )
                        }
                    ),
                    FileOperationConflictDialogFragment.getTitle(sourceFile, targetFile, service),
                    FileOperationConflictDialogFragment.getMessage(
                        sourceFile,
                        targetFile,
                        type,
                        service
                    ),
                    service
                )
            }
        }
    } catch (e: InterruptedException) {
        throw InterruptedIOException().apply { initCause(e) }
    }

internal fun FileOperationJob.resolveCopyMoveStructuralConflict(
    source: Path,
    target: Path,
    type: CopyMoveType,
    actionAllInfo: FileOperationActionAllInfo,
    kind: FileOperationStructuralConflictKind
): FileOperationStructuralConflictResolution {
    val skipAll = when (kind) {
        FileOperationStructuralConflictKind.INTO_SELF -> actionAllInfo.skipCopyMoveIntoItself
        FileOperationStructuralConflictKind.OVER_SELF -> actionAllInfo.skipCopyMoveOverItself
    }
    if (skipAll) {
        return FileOperationStructuralConflictResolution.Skip
    }
    val result = showErrorDialog(
        getString(
            when (kind) {
                FileOperationStructuralConflictKind.INTO_SELF -> type.getResourceId(
                    R.string.file_job_cannot_copy_into_itself_title,
                    R.string.file_job_cannot_extract_into_itself_title,
                    R.string.file_job_cannot_move_into_itself_title
                )

                FileOperationStructuralConflictKind.OVER_SELF -> type.getResourceId(
                    R.string.file_job_cannot_copy_over_itself_title,
                    R.string.file_job_cannot_extract_over_itself_title,
                    R.string.file_job_cannot_move_over_itself_title
                )
            }
        ),
        getString(
            when (kind) {
                FileOperationStructuralConflictKind.INTO_SELF ->
                    R.string.file_job_cannot_copy_move_into_itself_message

                FileOperationStructuralConflictKind.OVER_SELF ->
                    R.string.file_job_cannot_copy_move_over_itself_message
            }
        ),
        null,
        true,
        getString(R.string.skip),
        getString(android.R.string.cancel),
        null
    )
    return when (result.action) {
        FileOperationErrorAction.POSITIVE -> {
            if (result.isAll) {
                when (kind) {
                    FileOperationStructuralConflictKind.INTO_SELF -> {
                        actionAllInfo.skipCopyMoveIntoItself = true
                    }

                    FileOperationStructuralConflictKind.OVER_SELF -> {
                        actionAllInfo.skipCopyMoveOverItself = true
                    }
                }
            }
            FileOperationStructuralConflictResolution.Skip
        }

        FileOperationErrorAction.CANCELED,
        FileOperationErrorAction.NEGATIVE -> {
            requestCancellation()
            FileOperationStructuralConflictResolution.Interrupt
        }
        else -> throw AssertionError(result.action)
    }
}

@Throws(IOException::class)
internal fun FileOperationJob.resolveTargetAlreadyExistsConflict(
    source: Path,
    target: Path,
    type: CopyMoveType,
    actionAllInfo: FileOperationActionAllInfo,
    exception: FileAlreadyExistsException
): FileOperationTargetConflictResolution {
    val sourceFile = source.toAppPath().loadFileItem()
    val targetFile = target.toAppPath().loadFileItem()
    val sourceIsDirectory = sourceFile.attributesNoFollowLinks.isDirectory
    val targetIsDirectory = targetFile.attributesNoFollowLinks.isDirectory
    requireCompatibleReplacement(source, target, exception) { path ->
        if (path == source) sourceIsDirectory else targetIsDirectory
    }
    val isMerge = sourceIsDirectory && targetIsDirectory
    if (isMerge && actionAllInfo.merge) {
        return FileOperationTargetConflictResolution.MergeIntoDirectory
    }
    if (!isMerge && actionAllInfo.replace) {
        return FileOperationTargetConflictResolution.RetryWithReplace
    }
    if ((isMerge && actionAllInfo.skipMerge) || (!isMerge && actionAllInfo.skipReplace)) {
        return FileOperationTargetConflictResolution.Skip
    }
    val result = showConflictDialog(sourceFile, targetFile, type)
    return when (result.action) {
        FileOperationConflictAction.MERGE_OR_REPLACE -> {
            if (result.isAll) {
                if (isMerge) {
                    actionAllInfo.merge = true
                } else {
                    actionAllInfo.replace = true
                }
            }
            if (isMerge) {
                FileOperationTargetConflictResolution.MergeIntoDirectory
            } else {
                FileOperationTargetConflictResolution.RetryWithReplace
            }
        }

        FileOperationConflictAction.RENAME -> FileOperationTargetConflictResolution.RetryWithRename(
            target.resolveSibling(requireNotNull(result.name))
        )

        FileOperationConflictAction.SKIP -> {
            if (result.isAll) {
                if (isMerge) {
                    actionAllInfo.skipMerge = true
                } else {
                    actionAllInfo.skipReplace = true
                }
            }
            FileOperationTargetConflictResolution.Skip
        }

        FileOperationConflictAction.CANCELED,
        FileOperationConflictAction.CANCEL -> {
            requestCancellation()
            FileOperationTargetConflictResolution.Interrupt
        }
    }
}
