package com.wisso.wizefiles.feature.filejobs

import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException
import java.nio.file.AccessDeniedException
import com.wisso.wizefiles.provider.common.InvalidFileNameException

internal enum class FileOperationPhase {
    SCAN,
    PLAN,
    EXECUTE,
    CONFLICT,
    RECOVER,
    SKIP,
    RETRY,
    FINALIZE,
    ROLLBACK,
    CLEANUP,
    COMPLETE,
    FAILED,
    CANCELLED
}

internal enum class FileOperationType {
    COPY,
    MOVE,
    DELETE,
    ARCHIVE,
    EXTRACT,
    METADATA
}

internal enum class FileOperationFailureCategory {
    TRANSIENT,
    PERMISSION,
    VALIDATION,
    SECURITY,
    UNSUPPORTED,
    CANCELLED,
    METADATA_OPTIONAL,
    METADATA_REQUIRED,
    PROVIDER,
    UNKNOWN
}

internal sealed class FileOperationFailureDecision {
    object Retry : FileOperationFailureDecision()
    object Skip : FileOperationFailureDecision()
    object Abort : FileOperationFailureDecision()
    object RollbackAndAbort : FileOperationFailureDecision()
    object CleanupAndContinue : FileOperationFailureDecision()
}

internal data class FileOperationFailureContext(
    val type: FileOperationType,
    val sourcePath: String?,
    val targetPath: String?,
    val phase: FileOperationPhase,
    val exception: IOException,
    val retryCount: Int,
    val maxRetries: Int,
    val hasPartialOutput: Boolean,
    val userCancelled: Boolean,
    val metadataRequired: Boolean = false,
    val metadataOptional: Boolean = false,
    val allowSkip: Boolean = false
)

internal object FileOperationStatePolicy {
    const val MAX_RETRY_ATTEMPTS: Int = 2
    private const val MAX_CAUSE_DEPTH: Int = 16

    fun categorize(exception: IOException, metadataRequired: Boolean = false): FileOperationFailureCategory {
        val providerFailure = ProviderFailureBoundary.map(exception)
        val causes = exception.causeSequence().take(MAX_CAUSE_DEPTH).toList()
        if (causes.any { it is InvalidFileNameException }) {
            return FileOperationFailureCategory.VALIDATION
        }
        if (causes.any { it is AccessDeniedException }) {
            return FileOperationFailureCategory.PERMISSION
        }
        if (metadataRequired) {
            return FileOperationFailureCategory.METADATA_REQUIRED
        }
        if (providerFailure.retryClassification == com.wisso.wizefiles.storage.RetryClassification.TRANSIENT) {
            return FileOperationFailureCategory.TRANSIENT
        }
        if (causes.any { it is InterruptedIOException }) {
            return FileOperationFailureCategory.CANCELLED
        }
        return FileOperationFailureCategory.UNKNOWN
    }

    private fun Throwable.causeSequence(): Sequence<Throwable> = sequence {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = this@causeSequence
        while (current != null) {
            val cause = current
            if (!seen.add(cause)) {
                break
            }
            yield(cause)
            current = cause.cause
        }
    }

    fun decide(context: FileOperationFailureContext): FileOperationFailureDecision {
        if (context.userCancelled) {
            return FileOperationFailureDecision.Abort
        }
        if (context.metadataOptional) {
            return FileOperationFailureDecision.CleanupAndContinue
        }
        return when (categorize(context.exception, context.metadataRequired)) {
            FileOperationFailureCategory.CANCELLED,
            FileOperationFailureCategory.SECURITY,
            FileOperationFailureCategory.VALIDATION,
            FileOperationFailureCategory.UNSUPPORTED -> FileOperationFailureDecision.Abort

            FileOperationFailureCategory.PERMISSION,
            FileOperationFailureCategory.PROVIDER,
            FileOperationFailureCategory.UNKNOWN -> if (context.allowSkip) {
                FileOperationFailureDecision.Skip
            } else if (context.hasPartialOutput) {
                FileOperationFailureDecision.RollbackAndAbort
            } else {
                FileOperationFailureDecision.Abort
            }

            FileOperationFailureCategory.METADATA_REQUIRED -> if (context.retryCount < context.maxRetries) {
                FileOperationFailureDecision.Retry
            } else if (context.hasPartialOutput) {
                FileOperationFailureDecision.RollbackAndAbort
            } else {
                FileOperationFailureDecision.Abort
            }

            FileOperationFailureCategory.METADATA_OPTIONAL -> FileOperationFailureDecision.CleanupAndContinue
            FileOperationFailureCategory.TRANSIENT -> if (context.retryCount < context.maxRetries) {
                FileOperationFailureDecision.Retry
            } else {
                FileOperationFailureDecision.Abort
            }
        }
    }
}
