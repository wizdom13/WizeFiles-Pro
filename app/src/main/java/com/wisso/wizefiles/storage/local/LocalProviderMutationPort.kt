// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.local

import com.wisso.wizefiles.provider.common.InvalidFileNameException
import com.wisso.wizefiles.storage.FileNode
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.MetadataPreservation
import com.wisso.wizefiles.storage.MetadataPreservationReport
import com.wisso.wizefiles.storage.OperationCancellation
import com.wisso.wizefiles.storage.OperationCancelledException
import com.wisso.wizefiles.storage.OperationResult
import com.wisso.wizefiles.storage.PreservationStatus
import com.wisso.wizefiles.storage.ProviderFailureSignal
import com.wisso.wizefiles.storage.ProviderFailureSignalSource
import com.wisso.wizefiles.storage.ProviderJvmFailureClassifier
import com.wisso.wizefiles.storage.ProviderMutationCapabilities
import com.wisso.wizefiles.storage.ProviderMutationKind
import com.wisso.wizefiles.storage.ProviderMutationOutcome
import com.wisso.wizefiles.storage.ProviderMutationPort
import com.wisso.wizefiles.storage.ProviderOperationRunner
import com.wisso.wizefiles.storage.ResumeCheckpoint
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
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.NoSuchFileException

internal interface LocalNioOperations {
    fun size(path: Path): Long
    fun copy(source: Path, target: Path, vararg options: CopyOption)
    fun move(source: Path, target: Path, vararg options: CopyOption)
    fun delete(path: Path)
}

internal object SystemLocalNioOperations : LocalNioOperations {
    override fun size(path: Path): Long = if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) 0 else Files.size(path)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) { Files.copy(source, target, *options) }
    override fun move(source: Path, target: Path, vararg options: CopyOption) { Files.move(source, target, *options) }
    override fun delete(path: Path) { Files.delete(path) }
}

internal data class LocalMutationOptions(
    val replaceExisting: Boolean = false,
    val copyAttributes: Boolean = false,
    val atomicMove: Boolean = false
)

/** Real local-filesystem adapter for the provider-neutral mutation runner. */
internal class LocalProviderMutationPort(
    private val operations: LocalNioOperations = SystemLocalNioOperations,
    private val options: LocalMutationOptions = LocalMutationOptions()
) : ProviderMutationPort {
    override val capabilities = ProviderMutationCapabilities(
        supportedKinds = ProviderMutationKind.entries.toSet()
    )

    var isClosed: Boolean = false
        private set
    var failureCause: IOException? = null
        private set

    override fun execute(
        request: FileOperationRequest,
        checkpoint: ResumeCheckpoint,
        cancellation: OperationCancellation
    ): ProviderMutationOutcome {
        require(checkpoint == ResumeCheckpoint.EMPTY) { "Local NIO mutations do not support byte resume" }
        val source = request.source.localPath()
        return try {
            val bytes = if (request is FileOperationRequest.Delete) {
                0
            } else {
                cancellation.throwIfCancelled()
                operations.size(source)
            }
            cancellation.throwIfCancelled()
            when (request) {
                is FileOperationRequest.Copy -> operations.copy(source, request.target.localPath(), *copyOptions())
                is FileOperationRequest.Move -> operations.move(source, request.target.localPath(), *moveOptions())
                is FileOperationRequest.Delete -> operations.delete(source)
            }
            ProviderMutationOutcome.Complete(bytes, 1, metadataReport(request))
        } catch (exception: OperationCancelledException) {
            failureCause = InterruptedIOException(exception.message).apply { initCause(exception) }
            ProviderMutationOutcome.Cancelled(message = exception.message)
        } catch (exception: IOException) {
            failureCause = exception
            val signal = exception.toProviderFailureSignal()
            val destinationMayExist =
                request !is FileOperationRequest.Delete &&
                    exception !is AccessDeniedException &&
                    exception !is FileAlreadyExistsException &&
                    exception !is NoSuchFileException &&
                    exception !is AtomicMoveNotSupportedException
            if (signal == ProviderFailureSignal.INTERRUPTED) {
                ProviderMutationOutcome.Cancelled(
                    destinationMayExist = destinationMayExist,
                    message = exception.message
                )
            } else {
                ProviderMutationOutcome.Failed(
                    signal = signal,
                    destinationMayExist = destinationMayExist,
                    message = exception.message
                )
            }
        }
    }

    override fun close() { isClosed = true }

    private fun copyOptions(): Array<CopyOption> = buildList {
        add(LinkOption.NOFOLLOW_LINKS)
        if (options.replaceExisting) add(StandardCopyOption.REPLACE_EXISTING)
        if (options.copyAttributes) add(StandardCopyOption.COPY_ATTRIBUTES)
    }.toTypedArray()

    private fun moveOptions(): Array<CopyOption> = buildList {
        if (options.replaceExisting) add(StandardCopyOption.REPLACE_EXISTING)
        if (options.atomicMove) add(StandardCopyOption.ATOMIC_MOVE)
    }.toTypedArray()

    private fun metadataReport(request: FileOperationRequest): MetadataPreservationReport {
        if (request !is FileOperationRequest.Copy) return MetadataPreservationReport(emptyList())
        val status = if (options.copyAttributes) PreservationStatus.PRESERVED else PreservationStatus.NOT_REQUESTED
        return MetadataPreservationReport(
            listOf(
                MetadataPreservation(MetadataAttribute.CREATION_TIME, status),
                MetadataPreservation(MetadataAttribute.MODIFIED_TIME, status),
                MetadataPreservation(MetadataAttribute.ACCESS_TIME, status),
                MetadataPreservation(MetadataAttribute.DOS_ATTRIBUTES, status)
            )
        )
    }
}

/** Executes one local mutation through the shared invariants while preserving the legacy IOException API. */
@Throws(IOException::class)
internal fun runLocalMutation(
    request: FileOperationRequest,
    options: LocalMutationOptions = LocalMutationOptions(),
    cancellation: OperationCancellation = OperationCancellation.NONE
): OperationResult {
    val port = LocalProviderMutationPort(options = options)
    val result = try {
        ProviderOperationRunner.run(port, request, cancellation = cancellation)
    } catch (exception: OperationCancelledException) {
        throw InterruptedIOException(exception.message).apply { initCause(exception) }
    }
    if (result is OperationResult.Partial) {
        throw port.failureCause ?: IOException("Local provider mutation failed: ${result.failure.kind}")
    }
    return result
}

internal val threadInterruptionCancellation = OperationCancellation {
    Thread.currentThread().isInterrupted
}

internal data class LocalPathNode(internal val nioPath: Path) : FileNode {
    override val backendId: String = "local"
    override val path: String = nioPath.toString()
    override val name: String = nioPath.fileName?.toString().orEmpty()
}

private fun FileNode.localPath(): Path {
    require(backendId == "local") { "Local provider received a non-local node" }
    return (this as? LocalPathNode)?.nioPath ?: Paths.get(path)
}

internal fun IOException.toProviderFailureSignal(): ProviderFailureSignal {
    val causes = generateSequence<Throwable>(this) { it.cause }.take(16).toList()
    return if (causes.any { it is InvalidFileNameException }) ProviderFailureSignal.PERMANENT
    else ProviderJvmFailureClassifier.classify(this)
}
