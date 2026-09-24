package com.wisso.wizefiles.searchindex

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class SearchIndexForegroundPolicyRegressionTest {

    @Test
    fun `automatic indexing stays background and denied promotion is recoverable`() {
        val worker = source("searchindex/SearchIndexWorker.kt")
        val manager = source("searchindex/SearchIndexManager.kt")
        val settings = source("feature/settings/SettingsPreferenceFragment.kt")

        assertTrue(worker.contains("KEY_USER_INITIATED"))
        assertTrue(worker.contains("if (!inputData.getBoolean(KEY_USER_INITIATED, false))"))
        assertTrue(worker.contains("catch (exception: Exception)"))
        assertTrue(worker.contains("isRecoverableSearchIndexForegroundFailure"))
        assertTrue(worker.contains("continuing as scheduled background work"))
        assertTrue(worker.contains("if (!foregroundEnabled) return"))
        assertTrue(manager.contains("userInitiated: Boolean = false"))
        assertTrue(manager.contains("SearchIndexWorker.KEY_USER_INITIATED to userInitiated"))
        assertTrue(settings.contains("updateIndex(userInitiated = true)"))
        assertTrue(settings.contains("updateIndex(rebuild = true, userInitiated = true)"))
    }

    @Test
    fun `wrapped foreground denial is recoverable but cancellation is not`() {
        assertTrue(
            ExecutionException(IllegalStateException("foreground denied"))
                .isRecoverableSearchIndexForegroundFailure()
        )
        assertTrue(
            ExecutionException(SecurityException("foreground not permitted"))
                .isRecoverableSearchIndexForegroundFailure()
        )
        assertFalse(
            CancellationException("worker cancelled")
                .isRecoverableSearchIndexForegroundFailure()
        )
        assertFalse(IOException("unrelated failure").isRecoverableSearchIndexForegroundFailure())
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/$relativePath"),
            Paths.get("src/main/java/com/wisso/wizefiles/$relativePath")
        )
        val file = candidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate $relativePath")
        return String(Files.readAllBytes(file), StandardCharsets.UTF_8)
    }
}
