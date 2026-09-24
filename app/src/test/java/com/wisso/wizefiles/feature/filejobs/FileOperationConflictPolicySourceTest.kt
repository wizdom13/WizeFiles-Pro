package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationConflictPolicySourceTest {

    @Test
    fun conflictPolicyExtractedToDedicatedFile() {
        val source = readSource("FileOperationConflictPolicy.kt")
        assertTrue(source.contains("resolveCopyMoveStructuralConflict"))
        assertTrue(source.contains("resolveTargetAlreadyExistsConflict"))
    }

    @Test
    fun transferEngineUsesConflictPolicyHelpersForCopyMoveFlow() {
        val source = readSource("FileOperationTransferEngine.kt")
        assertTrue(source.contains("resolveCopyMoveStructuralConflict("))
        assertTrue(source.contains("resolveTargetAlreadyExistsConflict("))
    }

    private fun readSource(fileName: String): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName"),
            File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException(fileName)
        return file.readText()
    }
}
