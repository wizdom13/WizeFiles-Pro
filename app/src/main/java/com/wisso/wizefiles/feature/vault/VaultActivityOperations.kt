package com.wisso.wizefiles.vault

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal class VaultActivityOperations(
    context:Context,
    private val vaultId:String
) {
    private val manager=VaultManager(context.applicationContext)

    suspend fun deleteEntries(entries:List<VaultEntry>):Result<Unit> =
        withContext(Dispatchers.IO) {
            manager.deleteEntries(vaultId,entries.mapTo(linkedSetOf(),VaultEntry::id))
        }

    suspend fun importPaths(
        parentId:String?,
        paths:List<Path>
    ):VaultImportBatchResult = withContext(Dispatchers.IO) {
        manager.importPaths(vaultId,parentId,paths)
    }

    suspend fun deleteOriginals(paths:List<Path>):VaultOriginalDeletionResult =
        withContext(Dispatchers.IO) {
            val failures=paths.filterNot(VaultFileTree::deleteRecursively)
            VaultOriginalDeletionResult(paths.size-failures.size,paths.size,failures)
        }

    suspend fun exportEncryptedBackup(uri:Uri):Result<Unit> =
        withContext(Dispatchers.IO) {
            manager.exportEncryptedBackup(vaultId,uri)
        }
}

internal data class VaultOriginalDeletionResult(
    val deletedCount:Int,
    val totalCount:Int,
    val failedPaths:List<Path>
)
