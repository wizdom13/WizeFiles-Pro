package com.wisso.wizefiles.feature.filejobs

import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import com.wisso.wizefiles.core.app.BackgroundActivityStarter
import com.wisso.wizefiles.provider.common.PosixFileStore
import com.wisso.wizefiles.provider.common.ReadOnlyFileSystemException
import com.wisso.wizefiles.provider.common.UserActionRequiredException
import com.wisso.wizefiles.provider.common.getFileStore
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs
import kotlin.coroutines.resume

internal data class FileOperationErrorDialogSpec(
    val title: CharSequence,
    val message: CharSequence,
    val readOnlyFileStore: PosixFileStore?,
    val showAll: Boolean,
    val positiveButtonText: CharSequence?,
    val negativeButtonText: CharSequence?,
    val neutralButtonText: CharSequence?
)

internal enum class FileOperationRetryDecision {
    RETRY,
    COMPLETE,
    INTERRUPT
}

internal sealed class FileOperationRetryResult<out T> {
    data class Return<T>(val value: T) : FileOperationRetryResult<T>()
    object Retry : FileOperationRetryResult<Nothing>()
    object Interrupt : FileOperationRetryResult<Nothing>()
}

internal fun logFileOperationIOException(exception: Throwable) {
    val category = (exception as? IOException)?.let { FileOperationStatePolicy.categorize(it) }
    when (category) {
        FileOperationFailureCategory.PERMISSION -> {
            com.wisso.wizefiles.util.AppLog.w(
                "FileOperation",
                "Permission failure is handled by the operation decision flow",
                exception
            )
        }
        FileOperationFailureCategory.TRANSIENT -> {
            com.wisso.wizefiles.util.AppLog.w(
                "FileOperation",
                "Transient provider failure is handled by the operation decision flow",
                exception
            )
        }
        else -> com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", exception)
    }
}

internal inline fun FileOperationJob.runWithRetryPolicy(
    maxRetries: Int = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
    crossinline block: () -> Unit,
    crossinline onIOException: (IOException, Int) -> FileOperationRetryDecision
) {
    var retryCount = 0
    while (true) {
        try {
            block()
            return
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: IOException) {
            logFileOperationIOException(e)
            when (onIOException(e, retryCount)) {
                FileOperationRetryDecision.RETRY -> {
                    if (retryCount >= maxRetries) {
                        throw InterruptedIOException().apply { initCause(e) }
                    }
                    ++retryCount
                    continue
                }
                FileOperationRetryDecision.COMPLETE -> return
                FileOperationRetryDecision.INTERRUPT -> throw InterruptedIOException()
            }
        }
    }
}

internal inline fun <T> FileOperationJob.runWithRetryPolicyResult(
    maxRetries: Int = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
    crossinline block: () -> T,
    crossinline onIOException: (IOException, Int) -> FileOperationRetryResult<T>
): T {
    var retryCount = 0
    while (true) {
        try {
            return block()
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: IOException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            when (val result = onIOException(e, retryCount)) {
                FileOperationRetryResult.Retry -> {
                    if (retryCount >= maxRetries) {
                        throw InterruptedIOException().apply { initCause(e) }
                    }
                    ++retryCount
                    continue
                }
                is FileOperationRetryResult.Return -> return result.value
                FileOperationRetryResult.Interrupt -> throw InterruptedIOException()
            }
        }
    }
}

internal fun FileOperationJob.resolveRetryOrCancel(
    path: Path,
    exception: IOException,
    dialogSpec: FileOperationErrorDialogSpec
): FileOperationRetryDecision {
    if (exception is UserActionRequiredException) {
        val result = showUserAction(exception)
        if (result) {
            return FileOperationRetryDecision.RETRY
        }
    }
    if (!claimPermissionErrorDecision(path, exception)) {
        return FileOperationRetryDecision.INTERRUPT
    }
    val result = showErrorDialog(
        dialogSpec.title,
        dialogSpec.message,
        dialogSpec.readOnlyFileStore ?: getReadOnlyFileStore(path, exception),
        dialogSpec.showAll,
        dialogSpec.positiveButtonText,
        dialogSpec.negativeButtonText,
        dialogSpec.neutralButtonText
    )
    return when (result.action) {
        FileOperationErrorAction.POSITIVE -> FileOperationRetryDecision.RETRY
        FileOperationErrorAction.NEGATIVE,
        FileOperationErrorAction.CANCELED,
        FileOperationErrorAction.NEUTRAL -> {
            requestCancellation()
            FileOperationRetryDecision.INTERRUPT
        }
    }
}

internal fun <T> FileOperationJob.resolveRetryOrReturn(
    path: Path,
    exception: IOException,
    dialogSpec: FileOperationErrorDialogSpec,
    returnValue: T
): FileOperationRetryResult<T> {
    if (exception is UserActionRequiredException) {
        val result = showUserAction(exception)
        if (result) {
            return FileOperationRetryResult.Retry
        }
    }
    if (!claimPermissionErrorDecision(path, exception)) {
        return FileOperationRetryResult.Interrupt
    }
    val result = showErrorDialog(
        dialogSpec.title,
        dialogSpec.message,
        dialogSpec.readOnlyFileStore ?: getReadOnlyFileStore(path, exception),
        dialogSpec.showAll,
        dialogSpec.positiveButtonText,
        dialogSpec.negativeButtonText,
        dialogSpec.neutralButtonText
    )
    return when (result.action) {
        FileOperationErrorAction.POSITIVE -> FileOperationRetryResult.Retry
        FileOperationErrorAction.NEGATIVE,
        FileOperationErrorAction.CANCELED,
        FileOperationErrorAction.NEUTRAL -> {
            requestCancellation()
            FileOperationRetryResult.Interrupt
        }
    }
}

internal fun FileOperationJob.resolveRetrySkipOrCancel(
    path: Path,
    exception: IOException,
    dialogSpec: FileOperationErrorDialogSpec,
    onSkip: () -> Unit,
    onSkipAll: (() -> Unit)? = null
): FileOperationRetryDecision {
    if (exception is UserActionRequiredException) {
        val result = showUserAction(exception)
        if (result) {
            return FileOperationRetryDecision.RETRY
        }
    }
    if (!claimPermissionErrorDecision(path, exception)) {
        return FileOperationRetryDecision.INTERRUPT
    }
    val result = showErrorDialog(
        dialogSpec.title,
        dialogSpec.message,
        dialogSpec.readOnlyFileStore ?: getReadOnlyFileStore(path, exception),
        dialogSpec.showAll,
        dialogSpec.positiveButtonText,
        dialogSpec.negativeButtonText,
        dialogSpec.neutralButtonText
    )
    return when (result.action) {
        FileOperationErrorAction.POSITIVE -> FileOperationRetryDecision.RETRY
        FileOperationErrorAction.NEGATIVE -> {
            if (result.isAll) {
                onSkipAll?.invoke()
            }
            onSkip()
            FileOperationRetryDecision.COMPLETE
        }
        FileOperationErrorAction.CANCELED,
        FileOperationErrorAction.NEUTRAL -> {
            requestCancellation()
            FileOperationRetryDecision.INTERRUPT
        }
    }
}

// TODO: Requires dedicated user-action contracts from providers before invalid-name/remount errors can use automatic user-action retry.
@Throws(InterruptedIOException::class)
internal fun FileOperationJob.showUserAction(exception: UserActionRequiredException): Boolean =
    try {
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                val userAction = exception.getUserAction(continuation, service)
                BackgroundActivityStarter.startActivity(
                    userAction.intent, userAction.title, userAction.message, service
                )
            }
        }
    } catch (e: InterruptedException) {
        throw InterruptedIOException().apply { initCause(e) }
    }

@Throws(InterruptedIOException::class)
internal fun FileOperationJob.showErrorDialog(
    title: CharSequence,
    message: CharSequence,
    readOnlyFileStore: PosixFileStore?,
    showAll: Boolean,
    positiveButtonText: CharSequence?,
    negativeButtonText: CharSequence?,
    neutralButtonText: CharSequence?
): ErrorResult =
    try {
        runBlocking {
            suspendCancellableCoroutine { continuation ->
                BackgroundActivityStarter.startActivity(
                    FileOperationErrorDialogHostActivity::class.createIntent().putArgs(
                        FileOperationErrorDialogFragment.Args(
                            title, message, readOnlyFileStore, showAll, positiveButtonText,
                            negativeButtonText, neutralButtonText
                        ) { action, isAll ->
                            continuation.resume(ErrorResult(action, isAll))
                        }
                    ), title, message, service
                )
            }
        }
    } catch (e: InterruptedException) {
        throw InterruptedIOException().apply { initCause(e) }
    }

internal fun FileOperationJob.getReadOnlyFileStore(
    path: Path,
    exception: IOException
): PosixFileStore? {
    if (exception !is ReadOnlyFileSystemException || !path.isLinuxPath) {
        return null
    }
    val fileStore = try {
        path.getFileStore() as PosixFileStore
    } catch (e: IOException) {
        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        return null
    }
    return if (fileStore.isReadOnly) fileStore else null
}

internal data class ErrorResult(
    val action: FileOperationErrorAction,
    val isAll: Boolean
)

internal fun FileOperationJob.resolveTreeFailure(
    operationType: FileOperationType,
    phase: FileOperationPhase,
    path: Path,
    exception: IOException,
    allowSkip: Boolean,
    retryCount: Int = 0,
    hasPartialOutput: Boolean = false,
    metadataRequired: Boolean = false,
    metadataOptional: Boolean = false,
    onMetadataWarning: (() -> Unit)? = null,
    dialogSpec: FileOperationErrorDialogSpec,
    onSkip: () -> Unit,
    onSkipAll: (() -> Unit)? = null
): FileOperationRetryDecision {
    val decision = FileOperationStatePolicy.decide(
        FileOperationFailureContext(
            type = operationType,
            sourcePath = path.toString(),
            targetPath = null,
            phase = phase,
            exception = exception,
            retryCount = retryCount,
            maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
            hasPartialOutput = hasPartialOutput,
            userCancelled =
                FileOperationStatePolicy.categorize(exception) ==
                    FileOperationFailureCategory.CANCELLED,
            metadataRequired = metadataRequired,
            metadataOptional = metadataOptional,
            allowSkip = allowSkip
        )
    )
    when (decision) {
        FileOperationFailureDecision.Abort,
        FileOperationFailureDecision.RollbackAndAbort -> {
            throw InterruptedIOException().apply { initCause(exception) }
        }
        FileOperationFailureDecision.CleanupAndContinue -> {
            onMetadataWarning?.invoke()
            onSkip()
            return FileOperationRetryDecision.COMPLETE
        }
        else -> Unit
    }
    return resolveRetrySkipOrCancel(
        path,
        exception,
        dialogSpec,
        onSkip = onSkip,
        onSkipAll = onSkipAll
    )
}
