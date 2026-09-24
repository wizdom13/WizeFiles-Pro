package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationTodoPolicySourceTest {

    @Test
    fun retrySkipAbortTodosAreResolvedOrPrecise() {
        val metadata = readSource("FileOperationMetadataService.kt")
        val transfer = readSource("FileOperationTransferEngine.kt")
        val executor = readSource("FileOperationExecutor.kt")
        val policy = readSource("FileOperationErrorPolicy.kt")

        assertFalse(metadata.contains("TODO: Prompt retry, skip, skip-all or abort."))
        assertFalse(transfer.contains("TODO: Prompt retry, skip, skip-all or abort."))
        assertFalse(metadata.contains("TODO: Surface metadata partial-success warning in operation-complete UI."))
        assertFalse(transfer.contains("TODO: Surface metadata partial-success warning in operation-complete UI."))
        assertFalse(executor.contains("TODO: Prompt retry, skip, skip-all or abort."))
        assertTrue(policy.contains("TODO: Requires dedicated user-action contracts"))
    }

    private fun readSource(fileName: String): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName"),
            File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException(fileName)
        return file.readText()
    }
}
