package com.wisso.wizefiles.feature.sync

import java.io.File
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWorkerForegroundPolicyTest {
    @Test
    fun `scheduled worker does not request a foreground service`() {
        val source = sourceFile().readText()
        val scheduledWorker = source.substring(
            source.indexOf("internal class SyncRunWorker"),
            source.indexOf("internal class SyncResumeWorker")
        )

        assertFalse(scheduledWorker.contains("setForeground("))
        assertFalse(scheduledWorker.contains("tryEnterSyncForeground("))
    }

    @Test
    fun `resume worker tolerates platform foreground start denial`() {
        val source = sourceFile().readText()

        assertTrue(source.contains("tryEnterSyncForeground(run.profileId)"))
        assertTrue(source.contains("ForegroundServiceStartNotAllowedException"))
        assertTrue(source.contains("continuing as WorkManager background work"))
    }

    @Test
    fun `foreground failure classifier never swallows coroutine cancellation`() {
        assertFalse(CancellationException().isRecoverableSyncForegroundFailure())
        assertTrue(SecurityException().isRecoverableSyncForegroundFailure())
        assertTrue(IllegalStateException().isRecoverableSyncForegroundFailure())
    }

    private fun sourceFile(): File = listOf(
        File("src/main/java/com/wisso/wizefiles/feature/sync/SyncScheduler.kt"),
        File("app/src/main/java/com/wisso/wizefiles/feature/sync/SyncScheduler.kt")
    ).firstOrNull { it.exists() } ?: error("Missing SyncScheduler.kt")
}
