package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationCompletionWarningTest {

    @Test
    fun successWithZeroWarningsKeepsDefaultSuccessFlow() {
        val source = readSource("FileOperationJob.kt")
        assertTrue(source.contains("if (metadataWarningCount > 0)"))
    }

    @Test
    fun successWithMetadataWarningsShowsCompletedWithWarningsMessage() {
        val jobSource = readSource("FileOperationJob.kt")
        val executorSource = readSource("FileOperationNotifications.kt")
        val stringsSource = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        ).firstOrNull { it.exists() }?.readText() ?: throw FileNotFoundException("strings.xml")
        assertTrue(jobSource.contains("file_job_completion_with_metadata_warning"))
        assertTrue(jobSource.contains("postCompletionWarningNotification(metadataWarningCount)"))
        assertTrue(executorSource.contains("postCompletionWarningNotification"))
        assertTrue(executorSource.contains("setContentInfo(metadataWarningCount.toString())"))
        assertTrue(stringsSource.contains("Completed with warnings. Files were transferred"))
    }

    @Test
    fun warningsRemainSuccessNotFailureCancellation() {
        val jobSource = readSource("FileOperationJob.kt")
        assertFalse(jobSource.contains("throw InterruptedIOException()"))
        assertTrue(jobSource.contains("catch (e: InterruptedIOException)"))
        assertTrue(jobSource.contains("catch (e: Exception)"))
    }

    private fun readSource(fileName: String): String {
        val file = listOf(
            File("src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName"),
            File("app/src/main/java/com/wisso/wizefiles/feature/filejobs/$fileName")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException(fileName)
        return file.readText()
    }
}
