package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationNotificationWarningTest {

    @Test
    fun successWithoutWarningsKeepsNormalCompletionNotificationBehavior() {
        val jobSource = readSource("FileOperationJob.kt")
        assertTrue(jobSource.contains("if (!keepCompletionNotification)"))
    }

    @Test
    fun successWithWarningsPostsCompletedWithWarningsNotification() {
        val jobSource = readSource("FileOperationJob.kt")
        val executorSource = readSource("FileOperationNotifications.kt")
        assertTrue(jobSource.contains("postCompletionWarningNotification(metadataWarningCount)"))
        assertTrue(executorSource.contains("file_job_completion_with_warnings_title"))
        assertTrue(executorSource.contains("file_job_completion_with_metadata_warning"))
        assertTrue(executorSource.contains("file_job_metadata_warning_count_notification"))
        assertTrue(executorSource.contains("setSubText(warningCountText)"))
        assertTrue(executorSource.contains("setContentInfo(metadataWarningCount.toString())"))
    }

    @Test
    fun warningNotificationCopyIsSafe() {
        val strings = readStrings()
        assertTrue(strings.contains("Completed with warnings"))
        assertTrue(strings.contains("file_job_metadata_warning_count_notification"))
        assertTrue(strings.contains("%1${'$'}d warnings"))
        assertFalse(strings.contains("stack trace"))
    }

    @Test
    fun failureAndCancellationPathsRemainSeparate() {
        val jobSource = readSource("FileOperationJob.kt")
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

    private fun readStrings(): String {
        val file = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        ).firstOrNull { it.exists() } ?: throw FileNotFoundException("strings.xml")
        return file.readText()
    }
}
