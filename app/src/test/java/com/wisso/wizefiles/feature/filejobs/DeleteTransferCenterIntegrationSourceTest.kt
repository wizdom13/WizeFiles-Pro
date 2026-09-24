package com.wisso.wizefiles.feature.filejobs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteTransferCenterIntegrationSourceTest {
    @Test
    fun `delete is registered tracked recoverable and cleaned with history`() {
        val coordinator = sourceFile("feature/filejobs/FileOperationCommandCoordinator.kt")
        val job = sourceFile("feature/filejobs/FileTransferOperationJobs.kt")
        val recovery = sourceFile("feature/transfer/TransferRecoveryManager.kt")
        val repository = sourceFile("feature/transfer/TransferRepository.kt")
        val adapter = sourceFile("feature/transfer/TransferCenterAdapter.kt")
        val navigation = sourceFile("feature/transfer/TransferDestinationNavigation.kt")

        assertTrue("type = TransferOperationType.DELETE" in coordinator)
        assertTrue("DeleteOperationStore.save(operationSpec.id, options)" in coordinator)
        assertTrue("DeleteFileOperationJob(paths, options, operation.id)" in coordinator)

        assertTrue("FileOperationJob(transferId)" in job)
        assertTrue("beginTransferExecution(paths.size.toLong(), 0L)" in job)
        assertTrue("TransferItemTracker.begin(transferId, path, path)" in job)
        assertTrue("tracker?.start()" in job)
        assertTrue("tracker?.complete(path, 0L)" in job)

        assertTrue("TransferOperationType.DELETE ->" in recovery)
        assertTrue("DeleteOperationStore.load(operation.id)" in recovery)
        assertTrue("TransferOperationType.DELETE -> DeleteOperationStore.delete(id)" in repository)
        assertTrue("TransferOperationType.DELETE -> null" in adapter)
        assertTrue("TransferOperationType.DELETE" in navigation)
    }

    private fun sourceFile(relativePath: String): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/$relativePath"),
            File("app/src/main/java/com/wisso/wizefiles/$relativePath")
        ).firstOrNull(File::exists) ?: error("Unable to locate $relativePath")
        return file.readText()
    }
}
