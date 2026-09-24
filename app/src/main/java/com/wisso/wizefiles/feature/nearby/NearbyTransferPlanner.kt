package com.wisso.wizefiles.feature.nearby

import com.wisso.wizefiles.feature.sync.SyncPathResolver
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferItemRecord
import com.wisso.wizefiles.feature.transfer.TransferItemState
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.feature.transfer.TransferPlanningPolicy
import com.wisso.wizefiles.feature.transfer.TransferPlanningRequirements
import com.wisso.wizefiles.storage.ConflictPolicy
import com.wisso.wizefiles.storage.FileNode
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.KeepBothNaming
import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.OperationCancellation
import com.wisso.wizefiles.storage.OperationCancelledException
import com.wisso.wizefiles.storage.ProviderCapabilities
import com.wisso.wizefiles.storage.ProviderMutationCapabilities
import com.wisso.wizefiles.storage.ProviderMutationKind
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toUriString
import com.wisso.wizefiles.storage.StorageFacade
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

internal object NearbyTransferPlanner {
    fun planSend(
        sourceUris: List<String>,
        sessionId: String,
        cancellation: OperationCancellation = OperationCancellation.NONE
    ): NearbyOffer {
        require(sourceUris.isNotEmpty()) { "Choose at least one file or folder" }
        cancellation.throwIfCancelled()
        val operation = TransferRepository.enqueue(
            TransferOperationSpec(
                type = TransferOperationType.NEARBY_SEND,
                sourceUris = sourceUris,
                destinationUri = "nearby://pending"
            )
        )
        TransferRepository.transition(operation.id, TransferOperationState.PLANNING)
        return try {
            val entries = mutableListOf<NearbyManifestEntry>()
            val rootNames = mutableSetOf<String>()
            sourceUris.forEach { uri ->
            cancellation.throwIfCancelled()
            val root = requireNotNull(SyncPathResolver.resolve(uri)) { "Source is unavailable" }
            require(Files.exists(root, LinkOption.NOFOLLOW_LINKS)) { "Source is unavailable" }
            require(!Files.isSymbolicLink(root)) { "Symbolic links cannot be sent" }
            val rootName = uniqueName(root.fileName?.toString().orEmpty().ifBlank { "item" }, rootNames)
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes
                ): FileVisitResult {
                    cancellation.throwIfCancelled()
                    require(!attributes.isSymbolicLink) { "Symbolic links cannot be sent" }
                    addEntry(root, rootName, directory, attributes, entries)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    cancellation.throwIfCancelled()
                    require(!attributes.isSymbolicLink) { "Symbolic links cannot be sent" }
                    addEntry(root, rootName, file, attributes, entries)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                    cancellation.throwIfCancelled()
                    throw exception
                }

                override fun postVisitDirectory(
                    directory: Path,
                    exception: IOException?
                ): FileVisitResult {
                    cancellation.throwIfCancelled()
                    if (exception != null) throw exception
                    return FileVisitResult.CONTINUE
                }
            })
            }
            cancellation.throwIfCancelled()
            require(entries.size <= NearbyPathSecurity.MAX_ITEMS) { "The selection contains too many items" }
            val plan = planResolvedEntries(entries, cancellation)
            plan.requests.zip(entries).forEach { (request, entry) ->
                cancellation.throwIfCancelled()
                val target = requireNotNull((request as? FileOperationRequest.Copy)?.target)
                TransferDatabase.beginItem(
                    operation.id,
                    entry.sourceUri,
                    "nearby://${target.path}",
                    entry.relativePath,
                    entry.directory,
                    entry.sizeBytes,
                    entry.modifiedMillis,
                    entry.fingerprint
                )
            }
            var total = 0L
            entries.forEach { entry ->
                cancellation.throwIfCancelled()
                total = Math.addExact(total, entry.sizeBytes)
            }
            cancellation.throwIfCancelled()
            TransferRepository.updatePlanSummary(operation.id, entries.size.toLong(), total)
            TransferRepository.transition(operation.id, TransferOperationState.RUNNING)
            NearbyOffer(sessionId, operation.id, entries, total).also { NearbyProtocol.offer(it) }
        } catch (cancelled: OperationCancelledException) {
            runCatching {
                TransferRepository.transition(
                    operation.id,
                    TransferOperationState.CANCELLED,
                    reason = "Cancelled while preparing files"
                )
            }
            throw cancelled
        } catch (exception: Exception) {
            runCatching {
                TransferRepository.transition(
                    operation.id,
                    TransferOperationState.FAILED,
                    errorCategory = exception.javaClass.simpleName,
                    errorMessage = exception.message.orEmpty()
                )
            }
            throw exception
        }
    }

    internal fun planResolvedEntries(
        entries: List<NearbyManifestEntry>,
        cancellation: OperationCancellation = OperationCancellation.NONE
    ) = TransferPlanningPolicy.plan(
            requests = entries.asSequence().map { entry ->
                cancellation.throwIfCancelled()
                FileOperationRequest.Copy(
                    NearbyPlanningNode(
                        sourceBackendId(entry.sourceUri),
                        entry.relativePath,
                        entry.relativePath.substringAfterLast('/')
                    ),
                    NearbyPlanningNode("nearby", "pending/${entry.id}", entry.relativePath.substringAfterLast('/'))
                )
            }.asIterable(),
            conflictPolicy = ConflictPolicy.ASK,
            sourceCapabilities = READABLE_PROVIDER,
            destinationCapabilities = WRITABLE_PROVIDER,
            mutationCapabilities = NEARBY_MUTATION_CAPABILITIES,
            requirements = TransferPlanningRequirements(
                metadataIntent = setOf(MetadataAttribute.MODIFIED_TIME),
                resumeRequired = true
            ),
            cancellation = cancellation
        )

    private fun sourceBackendId(sourceUri: String): String = runCatching {
        val uri = URI.create(sourceUri)
        val scheme = uri.scheme?.lowercase() ?: return@runCatching "local"
        uri.host?.lowercase()?.let { "$scheme:$it" } ?: scheme
    }.getOrDefault("local")

    fun rebuildSend(operationId: String, sessionId: String): NearbyOffer {
        val items = TransferRepository.items(operationId)
        require(items.isNotEmpty()) { "Nearby transfer has no recoverable items" }
        val entries = items.map { item ->
            NearbyManifestEntry(
                id = remoteId(item),
                relativePath = item.relativePath,
                sourceUri = item.sourceUri,
                directory = item.isDirectory,
                sizeBytes = item.sizeBytes,
                modifiedMillis = item.modifiedMillis,
                fingerprint = item.sourceFingerprint
            )
        }
        return NearbyOffer(
            sessionId,
            operationId,
            entries,
            entries.fold(0L) { sum, entry -> Math.addExact(sum, entry.sizeBytes) }
        )
    }

    fun beginReceive(
        offer: NearbyOffer,
        destinationUri: String,
        policy: NearbyConflictPolicy
    ): String {
        val destination = requireNotNull(SyncPathResolver.resolve(destinationUri)) {
            "Destination is unavailable"
        }
        require(Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
            "Choose a destination folder"
        }
        NearbyPathSecurity.requireSafeDestination(destination, destination)
        val usable = runCatching { Files.getFileStore(destination).usableSpace }.getOrDefault(Long.MAX_VALUE)
        require(usable >= offer.totalBytes) { "Not enough free space at the destination" }
        val operation = TransferRepository.enqueue(
            TransferOperationSpec(
                type = TransferOperationType.NEARBY_RECEIVE,
                sourceUris = offer.entries.map { "nearby://${offer.sessionId}/${it.id}" },
                destinationUri = destinationUri
            )
        )
        TransferRepository.transition(operation.id, TransferOperationState.PLANNING)
        val reserved = mutableSetOf<Path>()
        val keptRoots = mutableMapOf<String, Path>()
        offer.entries.forEach { entry ->
            val requested = NearbyPathSecurity.resolveInside(destination, entry.relativePath)
            val segments = entry.relativePath.split('/')
            val keptRoot = if (policy == NearbyConflictPolicy.KEEP_BOTH) {
                keptRoots.getOrPut(segments.first()) {
                    val requestedRoot = destination.resolve(segments.first()).normalize()
                    if (Files.exists(requestedRoot, LinkOption.NOFOLLOW_LINKS)) {
                        uniqueRootTarget(requestedRoot, keptRoots.values.toSet())
                    } else {
                        requestedRoot
                    }
                }
            } else null
            val target = when {
                keptRoot != null -> segments.drop(1).fold(keptRoot) { parent, segment ->
                    parent.resolve(segment)
                }.normalize()
                !Files.exists(requested, LinkOption.NOFOLLOW_LINKS) && requested !in reserved -> requested
                policy == NearbyConflictPolicy.SKIP -> requested
                policy == NearbyConflictPolicy.REPLACE -> requested
                else -> uniqueTarget(requested, reserved)
            }
            NearbyPathSecurity.requireSafeDestination(destination, target)
            reserved.add(target)
            val item = TransferDatabase.beginItem(
                operationId = operation.id,
                sourceUri = "nearby://${offer.sessionId}/${entry.id}",
                targetUri = target.toAppPath().toUriString(),
                relativePath = entry.relativePath,
                isDirectory = entry.directory,
                sizeBytes = entry.sizeBytes,
                modifiedMillis = entry.modifiedMillis,
                sourceFingerprint = entry.fingerprint
            )
            when {
                Files.exists(requested, LinkOption.NOFOLLOW_LINKS) && policy == NearbyConflictPolicy.SKIP ->
                    TransferDatabase.skipItem(item.id)
                entry.directory -> {
                    if (policy == NearbyConflictPolicy.REPLACE &&
                        Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                    ) {
                        StorageFacade().delete(target)
                    }
                    Files.createDirectories(target)
                    NearbyPathSecurity.requireSafeDestination(destination, target)
                    TransferDatabase.completeItem(item.id, target.toAppPath().toUriString())
                }
                entry.sizeBytes == 0L -> {
                    target.parent?.let {
                        NearbyPathSecurity.requireSafeDestination(destination, it)
                        Files.createDirectories(it)
                        NearbyPathSecurity.requireSafeDestination(destination, it)
                    }
                    val temporary = target.resolveSibling(
                        ".wizefiles-part-${offer.sessionId.take(8)}-${item.id}"
                    )
                    NearbyPathSecurity.requireSafeDestination(destination, temporary)
                    Files.deleteIfExists(temporary)
                    Files.createFile(temporary)
                    TransferDatabase.setTemporaryTarget(item.id, temporary.toAppPath().toUriString())
                    finalizeReceived(item, temporary, policy)
                }
            }
        }
        TransferRepository.updatePlanSummary(operation.id, offer.entries.size.toLong(), offer.totalBytes)
        TransferRepository.transition(operation.id, TransferOperationState.RUNNING)
        return operation.id
    }

    fun offsets(operationId: String): Map<String, Long> = TransferRepository.items(operationId)
        .filterNot { it.isDirectory || it.state == TransferItemState.SKIPPED }
        .associate { remoteId(it) to it.bytesCompleted.coerceAtLeast(0) }

    fun itemForRemoteId(operationId: String, sessionId: String, itemId: String): TransferItemRecord? =
        TransferRepository.items(operationId).firstOrNull {
            it.sourceUri == "nearby://$sessionId/$itemId" || remoteId(it) == itemId
        }

    fun remoteId(item: TransferItemRecord): String =
        item.targetUri.substringAfterLast('/').takeIf { item.targetUri.startsWith("nearby://") }
            ?: UUID.nameUUIDFromBytes(
                "${item.relativePath}\u0000${item.sizeBytes}\u0000${item.modifiedMillis}"
                    .toByteArray(StandardCharsets.UTF_8)
            ).toString()

    fun requireSafeReceivePath(item: TransferItemRecord, path: Path): Path {
        val operation = requireNotNull(TransferRepository.operation(item.operationId)) {
            "Nearby receive operation vanished"
        }
        val destination = requireNotNull(SyncPathResolver.resolve(operation.destinationUri)) {
            "Destination vanished"
        }
        return NearbyPathSecurity.requireSafeDestination(destination, path)
    }

    fun finalizeReceived(item: TransferItemRecord, temporary: Path, policy: NearbyConflictPolicy) {
        val operation = requireNotNull(TransferRepository.operation(item.operationId)) {
            "Nearby receive operation vanished"
        }
        val destination = requireNotNull(SyncPathResolver.resolve(operation.destinationUri)) {
            "Destination vanished"
        }
        val target = requireNotNull(SyncPathResolver.resolve(item.targetUri)) { "Destination vanished" }
        NearbyPathSecurity.requireSafeDestination(destination, target)
        NearbyPathSecurity.requireSafeDestination(destination, temporary)
        target.parent?.let {
            NearbyPathSecurity.requireSafeDestination(destination, it)
            Files.createDirectories(it)
            NearbyPathSecurity.requireSafeDestination(destination, it)
        }
        if (policy == NearbyConflictPolicy.REPLACE &&
            Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
        ) {
            StorageFacade().delete(target)
        }
        val options = if (policy == NearbyConflictPolicy.REPLACE) {
            arrayOf(StandardCopyOption.REPLACE_EXISTING)
        } else {
            emptyArray()
        }
        NearbyPathSecurity.requireSafeDestination(destination, target)
        NearbyPathSecurity.requireSafeDestination(destination, temporary)
        Files.move(temporary, target, *options)
        TransferDatabase.completeItem(item.id, target.toAppPath().toUriString())
    }

    private fun addEntry(
        root: Path,
        rootName: String,
        path: Path,
        attributes: BasicFileAttributes,
        entries: MutableList<NearbyManifestEntry>
    ) {
        require(entries.size < NearbyPathSecurity.MAX_ITEMS) { "The selection contains too many items" }
        val suffix = if (path == root) "" else root.relativize(path).toString().replace('\\', '/')
        val relative = NearbyPathSecurity.requireSafeRelativePath(
            if (suffix.isEmpty()) rootName else "$rootName/$suffix"
        )
        val size = if (attributes.isDirectory) 0 else attributes.size().coerceAtLeast(0)
        val modified = attributes.lastModifiedTime().toMillis().coerceAtLeast(0)
        val id = UUID.nameUUIDFromBytes(
            "$relative\u0000$size\u0000$modified".toByteArray(StandardCharsets.UTF_8)
        ).toString()
        val uri = path.toAppPath().toUriString()
        val entry = NearbyManifestEntry(
            id, relative, uri, attributes.isDirectory, size, modified,
            "${if (attributes.isDirectory) "d" else "f"}:$size:$modified"
        )
        entries += entry
    }

    private fun uniqueName(requested: String, reserved: MutableSet<String>): String {
        var candidate = requested
        var suffix = 2
        while (!reserved.add(candidate)) candidate = "$requested ($suffix++)"
        return candidate
    }

    private fun uniqueTarget(requested: Path, reserved: Set<Path>): Path {
        val name = requested.fileName.toString()
        var index = 2
        var candidate = requested
        while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) || candidate in reserved) {
            candidate = requested.resolveSibling(KeepBothNaming.candidate(name, index))
            index++
        }
        return candidate
    }

    private fun uniqueRootTarget(requested: Path, reserved: Set<Path>): Path {
        val name = requested.fileName.toString()
        var index = 2
        var candidate = requested
        while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) || candidate in reserved) {
            candidate = requested.resolveSibling(KeepBothNaming.candidate(name, index, isDirectory = true))
            index++
        }
        return candidate
    }

    private data class NearbyPlanningNode(
        override val backendId: String,
        override val path: String,
        override val name: String
    ) : FileNode

    private val READABLE_PROVIDER = ProviderCapabilities(
        canRead = true,
        canWrite = false,
        canMoveAtomically = false,
        canWatch = false,
        preservesModifiedTime = true
    )
    private val WRITABLE_PROVIDER = ProviderCapabilities(
        canRead = false,
        canWrite = true,
        canMoveAtomically = false,
        canWatch = false,
        preservesModifiedTime = true
    )
    private val NEARBY_MUTATION_CAPABILITIES = ProviderMutationCapabilities(
        supportedKinds = setOf(ProviderMutationKind.COPY),
        supportsResume = true,
        supportsCrossProviderCopy = true
    )
}
