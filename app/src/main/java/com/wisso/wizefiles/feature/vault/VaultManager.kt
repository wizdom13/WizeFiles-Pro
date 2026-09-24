package com.wisso.wizefiles.vault

import android.content.Context
import android.net.Uri
import com.wisso.wizefiles.provider.common.isDirectory
import com.wisso.wizefiles.provider.common.newDirectoryStream
import com.wisso.wizefiles.provider.common.newInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.withLock

data class VaultImportBatchResult(
    val importedSources: List<Path>,
    val failedSources: List<Path>
)

data class VaultDirectoryListing(
    val entries: List<VaultEntry>,
    val directoryItemCounts: Map<String, Int>
)

internal fun countVaultDirectoryItems(entries: List<VaultEntry>): Map<String, Int> {
    val countsByParent = entries.groupingBy(VaultEntry::parentId).eachCount()
    return entries.asSequence()
        .filter(VaultEntry::isDirectory)
        .associate { it.id to (countsByParent[it.id] ?: 0) }
}

internal object VaultMutationLocks {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun <T> withLock(vaultId: String, block: () -> T): T =
        locks.computeIfAbsent(vaultId) { ReentrantLock() }.withLock(block)
}

class VaultManager(private val context: Context, private val repository: VaultRepository = VaultRepository(context)) {

    fun listVaults(): List<VaultMetadata> = repository.listMetadatas().sortedBy { it.createdAt }

    fun createVault(name: String, password: CharArray): VaultMetadata {
        val creation = repository.createVault(name, password)
        VaultSessionManager.putUnlockedKey(creation.metadata.vaultId, creation.vmk)
        return creation.metadata
    }

    fun unlockWithPassword(vaultId: String, password: CharArray): Result<Unit> = runCatching {
        VaultMutationLocks.withLock(vaultId) {
            val metadata = repository.loadMetadata(vaultId)
            val vmk = repository.unwrapVmkWithPassword(metadata, password)
            VaultSessionManager.putUnlockedKey(vaultId, vmk)
        }
    }

    fun lock(vaultId: String) {
        VaultMutationLocks.withLock(vaultId) {
            VaultSessionManager.lock(vaultId)
        }
    }

    fun listEntries(vaultId: String, parentId: String?): List<VaultEntry> =
        listEntriesWithDirectoryCounts(vaultId, parentId).entries

    fun listEntriesWithDirectoryCounts(
        vaultId: String,
        parentId: String?
    ): VaultDirectoryListing = VaultMutationLocks.withLock(vaultId) {
        val key = VaultSessionManager.getUnlockedKey(vaultId)
            ?: return@withLock VaultDirectoryListing(emptyList(), emptyMap())
        val index = repository.readIndex(vaultId, key)
        val visibleEntries = index
            .filter { it.parentId == parentId }
            .sortedWith(compareBy<VaultEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        val allDirectoryCounts = countVaultDirectoryItems(index)
        VaultDirectoryListing(
            entries = visibleEntries,
            directoryItemCounts = visibleEntries.asSequence()
                .filter(VaultEntry::isDirectory)
                .associate { it.id to allDirectoryCounts.getValue(it.id) }
        )
    }

    fun createFolder(
        vaultId: String,
        parentId: String?,
        name: String
    ): Result<Unit> = updateIndex(vaultId) { entries ->
        VaultEntryNamePolicy.requireAvailable(entries, parentId, name)
        entries += createDirectoryEntry(parentId, name)
    }

    fun importFile(vaultId: String, parentId: String?, name: String, bytes: ByteArray): Result<Unit> =
        ByteArrayInputStream(bytes).use { input -> importFile(vaultId, parentId, name, input) }

    fun importFile(vaultId: String, parentId: String?, name: String, input: InputStream): Result<Unit> =
        updateIndex(vaultId) { entries ->
            VaultEntryNamePolicy.requireAvailable(entries, parentId, name)
            entries += createFileEntry(vaultId, parentId, name, input)
        }

    fun importPaths(vaultId: String, parentId: String?, sources: List<Path>): VaultImportBatchResult =
        mutateIndex(vaultId) { entries ->
            val importedSources = mutableListOf<Path>()
            val failedSources = mutableListOf<Path>()
            for (source in sources) {
                val snapshot = entries.toList()
                val imported = runCatching {
                    importPathToEntries(vaultId, source, parentId, entries)
                }.isSuccess
                if (imported) {
                    importedSources.add(source)
                } else {
                    entries.clear()
                    entries.addAll(snapshot)
                    repository.reconcileObjects(vaultId, referencedObjectIds(snapshot))
                    failedSources.add(source)
                }
            }
            VaultImportBatchResult(importedSources, failedSources)
        }

    private fun importPathToEntries(
        vaultId: String,
        source: Path,
        parentId: String?,
        entries: MutableList<VaultEntry>
    ) {
        if (source.isDirectory()) {
            val name = source.fileName?.toString().orEmpty()
            VaultEntryNamePolicy.requireAvailable(entries, parentId, name)
            val directoryEntry = createDirectoryEntry(parentId, name)
            entries += directoryEntry
            source.newDirectoryStream().use { stream ->
                for (child in stream) {
                    importPathToEntries(vaultId, child, directoryEntry.id, entries)
                }
            }
            return
        }
        val name = source.fileName?.toString()?.ifBlank { null } ?: "imported"
        VaultEntryNamePolicy.requireAvailable(entries, parentId, name)
        source.newInputStream().use { input ->
            entries += createFileEntry(vaultId, parentId, name, input)
        }
    }

    private fun createDirectoryEntry(parentId: String?, name: String): VaultEntry =
        VaultEntry(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            name = name,
            isDirectory = true,
            objectId = null,
            size = 0,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

    private fun createFileEntry(vaultId: String, parentId: String?, name: String, input: InputStream): VaultEntry {
        val key = requireNotNull(VaultSessionManager.getUnlockedKey(vaultId))
        val objectId = UUID.randomUUID().toString().replace("-", "")
        val size = repository.writeEncryptedObject(vaultId, objectId, key, input)
        return VaultEntry(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            name = name,
            isDirectory = false,
            objectId = objectId,
            size = size,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun readFile(vaultId: String, entryId: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            copyFileTo(vaultId, entryId, output)
            output.toByteArray()
        }

    fun copyFileTo(vaultId: String, entryId: String, output: OutputStream): Long =
        VaultMutationLocks.withLock(vaultId) {
            val key = requireNotNull(VaultSessionManager.getUnlockedKey(vaultId))
            val index = repository.readIndex(vaultId, key)
            val entry = index.first { it.id == entryId && !it.isDirectory }
            repository.copyDecryptedObjectTo(
                vaultId,
                requireNotNull(entry.objectId),
                key,
                output
            )
        }

    fun replaceFile(vaultId: String, entryId: String, bytes: ByteArray): Result<Unit> =
        ByteArrayInputStream(bytes).use { input -> replaceFile(vaultId, entryId, input) }

    fun replaceFile(vaultId: String, entryId: String, input: InputStream): Result<Unit> = updateIndex(vaultId) { entries ->
        val index = entries.indexOfFirst { it.id == entryId && !it.isDirectory }
        if (index == -1) {
            throw IllegalArgumentException("File entry not found")
        }
        val existing = entries[index]
        val replacement = createFileEntry(vaultId, existing.parentId, existing.name, input)
        entries[index] = existing.copy(
            objectId = replacement.objectId,
            size = replacement.size,
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun deleteEntry(vaultId: String, entryId: String): Result<Unit> =
        deleteEntries(vaultId, setOf(entryId))

    fun deleteEntries(vaultId: String, entryIds: Set<String>): Result<Unit> =
        updateIndex(vaultId) { entries ->
            val entryIdsToDelete = entryIds.toMutableSet()
            while (true) {
                val descendantIds = entries.asSequence()
                    .filter { entry ->
                        entry.parentId?.let(entryIdsToDelete::contains) == true
                    }
                    .map(VaultEntry::id)
                    .toSet()
                if (!entryIdsToDelete.addAll(descendantIds)) {
                    break
                }
            }
            entries.removeIf { it.id in entryIdsToDelete }
        }

    fun renameEntry(vaultId: String, entryId: String, newName: String): Result<Unit> = updateIndex(vaultId) { entries ->
        val index = entries.indexOfFirst { it.id == entryId }
        if (index == -1) return@updateIndex
        val existing = entries[index]
        VaultEntryNamePolicy.requireAvailable(
            entries,
            existing.parentId,
            newName,
            excludedEntryId = entryId
        )
        entries[index] = existing.copy(name = newName, modifiedAt = System.currentTimeMillis())
    }

    fun saveBiometricWrappedVmk(vaultId: String, wrappedVmk: ByteArray, iv: ByteArray): Result<Unit> = runCatching {
        VaultMutationLocks.withLock(vaultId) {
            val metadata = repository.loadMetadata(vaultId)
            repository.writeMetadata(
                metadata.copy(
                    biometricEnabled = true,
                    biometricWrappedVmkBase64 = VaultCrypto.toBase64(wrappedVmk),
                    biometricIvBase64 = VaultCrypto.toBase64(iv)
                )
            )
        }
    }

    fun deleteVault(vaultId: String): Result<Unit> = runCatching {
        VaultMutationLocks.withLock(vaultId) {
            val metadata = repository.loadMetadata(vaultId)
            repository.deleteVault(vaultId)
            VaultSessionManager.lock(vaultId)
            VaultKeystore.deleteKey(metadata.biometricKeyAlias)
        }
    }

    fun exportEncryptedBackup(vaultId: String, uri: Uri): Result<Unit> = runCatching {
        VaultMutationLocks.withLock(vaultId) {
            val root = repository.vaultDir(vaultId)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                ZipOutputStream(output).use { zip ->
                    root.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = file.relativeTo(root).path
                        zip.putNextEntry(ZipEntry(relative))
                        FileInputStream(file).use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: error("Unable to open output stream")
        }
    }

    private fun updateIndex(vaultId: String, block: (MutableList<VaultEntry>) -> Unit): Result<Unit> =
        runCatching { mutateIndex(vaultId, block) }

    private fun <T> mutateIndex(vaultId: String, block: (MutableList<VaultEntry>) -> T): T =
        VaultMutationLocks.withLock(vaultId) {
            val key = requireNotNull(VaultSessionManager.getUnlockedKey(vaultId))
            val entries = repository.readIndex(vaultId, key)
            val previousObjectIds = referencedObjectIds(entries)
            try {
                val result = block(entries)
                val nextObjectIds = referencedObjectIds(entries)
                repository.writeEncryptedIndex(vaultId, key, entries)
                repository.reconcileObjects(vaultId, nextObjectIds)
                result
            } catch (throwable: Throwable) {
                repository.reconcileObjects(vaultId, previousObjectIds)
                throw throwable
            }
        }

    private fun referencedObjectIds(entries: List<VaultEntry>): Set<String> =
        entries.asSequence()
            .filterNot(VaultEntry::isDirectory)
            .mapNotNull(VaultEntry::objectId)
            .toSet()

    internal fun copyUnlockedKey(vaultId: String): ByteArray =
        VaultMutationLocks.withLock(vaultId) {
            requireNotNull(VaultSessionManager.getUnlockedKey(vaultId)).copyOf()
        }
}
