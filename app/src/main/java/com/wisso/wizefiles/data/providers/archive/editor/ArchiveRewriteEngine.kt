package com.wisso.wizefiles.provider.archive.editor

import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.provider.archive.archiver.ArchiveReader
import com.wisso.wizefiles.provider.archive.archiver.ArchiveWriter
import com.wisso.wizefiles.provider.common.deleteIfExists
import com.wisso.wizefiles.provider.common.newByteChannel
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.storage.path.toUriString
import java.io.IOException
import java.io.InterruptedIOException
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

data class ArchiveRewriteProgress(
    val phase: ArchiveEditPhase,
    val currentEntry: String,
    val processedBytes: Long,
    val totalBytes: Long
)

class ArchiveRewriteEngine(
    private val spec: ArchiveMutationSpec,
    private val onProgress: (ArchiveRewriteProgress) -> Unit = {},
    private val checkInterrupted: () -> Unit = {
        if (Thread.interrupted()) throw InterruptedIOException()
    }
) {
    private val archiveFile: Path = spec.archiveUri.toAppPathOrNull()?.toLegacyPathOrNull()
        ?: throw IOException("Archive path is unavailable")
    private val capability = ArchiveEditCapabilities.forArchiveFile(archiveFile)
    private var processedBytes = 0L

    @Throws(IOException::class)
    fun execute(): ArchiveMutationPlan {
        if (!capability.editable) throw IOException(capability.reason)
        val archiveLock = locks.computeIfAbsent(spec.archiveUri) { ReentrantLock() }
        try {
            archiveLock.lockInterruptibly()
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Archive update interrupted while waiting").apply {
                initCause(exception)
            }
        }
        try {
            if (recoverInterruptedCommit()) {
                deleteMovedSources()
                ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMPLETED)
                return ArchiveMutationPlan(emptyList(), emptyList())
            }
            if (spec.phase == ArchiveEditPhase.COMMITTED || spec.phase == ArchiveEditPhase.COMPLETED) {
                deleteMovedSources()
                ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMPLETED)
                return ArchiveMutationPlan(emptyList(), emptyList())
            }
            verifyFingerprint()
            ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.PLANNING)
            val metadata = ArchiveReader.entryMetadata(archiveFile)
            if (metadata.any { it.isEncrypted }) throw IOException("Encrypted archives are read-only")
            val additions = expandAdditions(spec.mutations)
            val plan = ArchiveMutationPlanner.plan(
                existing = metadata.map { entry ->
                    ArchiveNamespaceEntry(entry.name, entry.isDirectory, entry.size)
                },
                mutations = spec.mutations,
                additions = additions,
                conflictPolicy = spec.conflictPolicy
            )
            val temporary = temporaryPath("edit")
            val backup = temporaryPath("backup")
            temporary.deleteIfExists()
            backup.deleteIfExists()
            ensureStagingSpace(temporary, plan)
            rebuild(plan, temporary)
            validate(temporary, plan)
            commit(temporary, backup)
            ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMMITTED)
            deleteMovedSources()
            ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMPLETED)
            return plan
        } finally {
            archiveLock.unlock()
        }
    }

    private fun verifyFingerprint() {
        if (spec.originalFingerprint.isNotEmpty() && fingerprint(archiveFile) != spec.originalFingerprint) {
            throw IOException("Archive changed after this update was planned")
        }
    }

    private fun ensureStagingSpace(temporary: Path, plan: ArchiveMutationPlan) {
        val existingArchiveBytes = runCatching { Files.size(archiveFile) }.getOrDefault(0L)
        val addedBytes = plan.entries.filter { it.isAddition }.sumOf { it.size.coerceAtLeast(0) }
        val required = existingArchiveBytes.coerceAtLeast(0) + addedBytes + MINIMUM_FREE_MARGIN_BYTES
        val parent = temporary.parent ?: return
        val usable = runCatching { Files.getFileStore(parent).usableSpace }.getOrNull() ?: return
        if (usable < required) throw IOException("Not enough temporary space to rebuild archive")
    }

    private fun recoverInterruptedCommit(): Boolean {
        if (spec.phase != ArchiveEditPhase.COMMITTING) return false
        val journal = ArchiveMutationStore.loadJournal(spec.operationId) ?: return false
        val temporary = journal.temporary.toAppPathOrNull()?.toLegacyPathOrNull() ?: return false
        val backup = journal.backup.toAppPathOrNull()?.toLegacyPathOrNull() ?: return false
        val originalExists = Files.exists(archiveFile, LinkOption.NOFOLLOW_LINKS)
        val temporaryExists = Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)
        val backupExists = Files.exists(backup, LinkOption.NOFOLLOW_LINKS)
        when {
            originalExists && backupExists -> {
                return try {
                    ArchiveReader.entryMetadata(archiveFile)
                    temporary.deleteIfExists()
                    backup.deleteIfExists()
                    ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMMITTED)
                    true
                } catch (_: IOException) {
                    copyReplacing(backup, archiveFile)
                    ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.PLANNING)
                    false
                }
            }
            originalExists && !temporaryExists && !backupExists -> {
                ArchiveReader.entryMetadata(archiveFile)
                ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMMITTED)
                return true
            }
            !originalExists && backupExists && temporaryExists -> {
                Files.move(temporary, archiveFile, StandardCopyOption.REPLACE_EXISTING)
                ArchiveReader.entryMetadata(archiveFile)
                backup.deleteIfExists()
                ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMMITTED)
                return true
            }
            !originalExists && backupExists -> {
                Files.move(backup, archiveFile, StandardCopyOption.REPLACE_EXISTING)
                ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.PLANNING)
                return false
            }
        }
        return false
    }

    private fun expandAdditions(mutations: List<ArchiveMutation>): List<PlannedArchiveEntry> =
        buildList {
            mutations.filter { it.type == ArchiveMutationType.ADD }.forEach { mutation ->
                val source = mutation.sourceUri.toAppPathOrNull()?.toLegacyPathOrNull()
                    ?: throw IOException("Paste source is unavailable")
                val targetRoot = ArchiveMutationPlanner.normalize(mutation.path, Files.isDirectory(source))
                if (Files.isSymbolicLink(source)) throw IOException("Symbolic links cannot be added to archives")
                if (Files.isDirectory(source)) {
                    Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            checkInterrupted()
                            val relative = source.relativize(dir).toString().replace('\\', '/')
                            val target = if (relative.isEmpty()) targetRoot else "$targetRoot/$relative"
                            add(PlannedArchiveEntry(null, target, true, dir.toAppPath().toUriString(), 0, mutation.deleteSourceAfterCommit))
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            checkInterrupted()
                            if (attrs.isSymbolicLink) throw IOException("Symbolic links cannot be added to archives")
                            val relative = source.relativize(file).toString().replace('\\', '/')
                            add(PlannedArchiveEntry(null, "$targetRoot/$relative", false, file.toAppPath().toUriString(), attrs.size(), mutation.deleteSourceAfterCommit))
                            return FileVisitResult.CONTINUE
                        }
                    })
                } else {
                    add(
                        PlannedArchiveEntry(
                            null,
                            targetRoot,
                            false,
                            mutation.sourceUri,
                            Files.size(source),
                            mutation.deleteSourceAfterCommit
                        )
                    )
                }
            }
        }

    private fun rebuild(plan: ArchiveMutationPlan, temporary: Path) {
        ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.REBUILDING)
        onProgress(ArchiveRewriteProgress(ArchiveEditPhase.REBUILDING, "", 0, plan.totalBytes))
        val byOriginal = plan.entries.filterNot { it.isAddition }.associateBy { it.originalPath!! }
        temporary.newByteChannel(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            ArchiveWriter(channel, capability.format!!.format, capability.format.filter, null).use { writer ->
                ArchiveReader.forEachEntry(archiveFile, emptyList()) { entry, data ->
                    checkInterrupted()
                    val original = ArchiveMutationPlanner.normalize(entry.name, entry.isDirectory)
                    val planned = byOriginal.entries.firstOrNull {
                        it.key.equals(original, ignoreCase = true)
                    }?.value ?: return@forEachEntry
                    writer.write(entry, planned.finalPath, data, PROGRESS_INTERVAL_MILLIS) { delta ->
                        processedBytes += delta
                        onProgress(
                            ArchiveRewriteProgress(
                                ArchiveEditPhase.REBUILDING,
                                planned.finalPath,
                                processedBytes,
                                plan.totalBytes
                            )
                        )
                        checkInterrupted()
                    }
                }
                plan.entries.filter { it.isAddition }.forEach { addition ->
                    checkInterrupted()
                    if (addition.isDirectory) {
                        writer.writeDirectory(addition.finalPath)
                    } else {
                        val source = addition.sourceUri.toAppPathOrNull()?.toLegacyPathOrNull()
                            ?: throw IOException("Paste source is unavailable")
                        writer.write(source, java.nio.file.Paths.get(addition.finalPath), PROGRESS_INTERVAL_MILLIS) { delta ->
                            processedBytes += delta
                            onProgress(ArchiveRewriteProgress(ArchiveEditPhase.REBUILDING, addition.finalPath, processedBytes, plan.totalBytes))
                            checkInterrupted()
                        }
                    }
                }
            }
        }
    }

    private fun validate(temporary: Path, plan: ArchiveMutationPlan) {
        ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.VALIDATING)
        onProgress(ArchiveRewriteProgress(ArchiveEditPhase.VALIDATING, "", processedBytes, plan.totalBytes))
        val actual = ArchiveReader.entryMetadata(temporary).mapNotNull { entry ->
            ArchiveMutationPlanner.normalize(entry.name, entry.isDirectory).takeIf(String::isNotEmpty)
        }.toSet()
        val expected = plan.finalPaths
        if (actual.map { it.lowercase() }.toSet() != expected.map { it.lowercase() }.toSet()) {
            throw IOException("Rebuilt archive manifest does not match the planned result")
        }
        ArchiveReader.forEachEntry(temporary, emptyList()) { _, data ->
            if (data != null) {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (data.read(buffer) != -1) checkInterrupted()
            }
        }
    }

    private fun commit(temporary: Path, backup: Path) {
        ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMMITTING)
        onProgress(ArchiveRewriteProgress(ArchiveEditPhase.COMMITTING, archiveFile.fileName.toString(), processedBytes, processedBytes))
        ArchiveMutationStore.saveJournal(spec.operationId, spec.archiveUri, temporary.toUri().toString(), backup.toUri().toString(), "PREPARED")
        if (temporary.fileSystem != archiveFile.fileSystem) {
            commitThroughLocalBackup(temporary, backup)
            ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMMITTED)
            return
        }
        try {
            Files.move(temporary, archiveFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            ArchiveMutationStore.updatePhase(spec.operationId, ArchiveEditPhase.COMMITTED)
            return
        } catch (_: Exception) {
            // Fall through to the journaled provider-compatible swap.
        }
        var originalMoved = false
        try {
            Files.move(archiveFile, backup, StandardCopyOption.REPLACE_EXISTING)
            originalMoved = true
            ArchiveMutationStore.saveJournal(spec.operationId, spec.archiveUri, temporary.toUri().toString(), backup.toUri().toString(), "ORIGINAL_BACKED_UP")
            Files.move(temporary, archiveFile, StandardCopyOption.REPLACE_EXISTING)
            ArchiveReader.entryMetadata(archiveFile)
            backup.deleteIfExists()
        } catch (failure: Exception) {
            if (originalMoved && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                runCatching {
                    archiveFile.deleteIfExists()
                    Files.move(backup, archiveFile, StandardCopyOption.REPLACE_EXISTING)
                }.onFailure(failure::addSuppressed)
            }
            throw IOException("Unable to commit rebuilt archive; the original was preserved", failure)
        }
    }

    private fun commitThroughLocalBackup(temporary: Path, backup: Path) {
        Files.copy(archiveFile, backup, StandardCopyOption.REPLACE_EXISTING)
        ArchiveMutationStore.saveJournal(spec.operationId, spec.archiveUri, temporary.toUri().toString(), backup.toUri().toString(), "ORIGINAL_BACKED_UP")
        try {
            copyReplacing(temporary, archiveFile)
            ArchiveReader.entryMetadata(archiveFile)
            temporary.deleteIfExists()
            backup.deleteIfExists()
        } catch (failure: Exception) {
            runCatching { copyReplacing(backup, archiveFile) }.onFailure(failure::addSuppressed)
            throw IOException("Unable to commit rebuilt archive; the recovery backup was retained", failure)
        }
    }

    private fun copyReplacing(source: Path, target: Path) {
        Files.newInputStream(source, StandardOpenOption.READ).use { input ->
            Files.newOutputStream(
                target,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { output -> input.copyTo(output) }
        }
    }

    private fun deleteMovedSources() {
        val roots = spec.mutations.filter {
            it.type == ArchiveMutationType.ADD && it.deleteSourceAfterCommit
        }.mapNotNull { it.sourceUri.toAppPathOrNull()?.toLegacyPathOrNull() }.distinct()
        roots.forEach { source ->
            runCatching {
                Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        Files.deleteIfExists(file)
                        return FileVisitResult.CONTINUE
                    }
                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        if (exc != null) throw exc
                        Files.deleteIfExists(dir)
                        return FileVisitResult.CONTINUE
                    }
                })
            }
        }
    }

    private fun temporaryPath(kind: String): Path {
        val name = archiveFile.fileName.toString()
        val suffix = spec.operationId.replace("-", "").take(12)
        return if (archiveFile.fileSystem.provider().scheme.equals("file", ignoreCase = true)) {
            archiveFile.resolveSibling(".$name.wizefiles-$kind-$suffix")
        } else {
            File(application.noBackupFilesDir, "archive-edits/staging").apply { mkdirs() }
                .resolve(".$name.wizefiles-$kind-$suffix")
                .toPath()
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL_MILLIS = 200L
        private const val MINIMUM_FREE_MARGIN_BYTES = 16L * 1024 * 1024
        private val locks = ConcurrentHashMap<String, ReentrantLock>()

        fun fingerprint(path: Path): String {
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            val raw = "${attrs.size()}:${attrs.lastModifiedTime().toMillis()}:${path.fileName}"
            return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
