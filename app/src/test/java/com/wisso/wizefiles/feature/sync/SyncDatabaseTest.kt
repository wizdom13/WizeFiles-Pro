package com.wisso.wizefiles.feature.sync

import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncDatabaseTest {
    @Before
    fun setUp() {
        setGlobalApplicationForTests(ApplicationProvider.getApplicationContext())
        TransferDatabase.clearForTests()
    }

    @After
    fun tearDown() {
        TransferDatabase.clearForTests()
    }

    @Test
    fun profileRunSnapshotAndPlanSurviveDatabaseReopen() {
        val profile = SyncProfile(
            name = "Phone backup",
            sourceUri = "file:///storage/docs",
            destinationUri = "rclone://drive/backup",
            mode = SyncMode.UPDATE_DESTINATION
        )
        SyncRepository.saveProfile(profile)
        val run = SyncRepository.createRun(
            SyncRun(
                profileId = profile.id,
                trigger = SyncRunTrigger.MANUAL,
                baselineBefore = 0
            )
        )
        SyncRepository.addSnapshotEntries(
            listOf(
                SyncSnapshotEntry(
                    profileId = profile.id,
                    generation = 1,
                    side = SyncSide.SOURCE,
                    relativePath = "report.pdf",
                    isDirectory = false,
                    sizeBytes = 42,
                    modifiedAtMillis = 100,
                    modifiedPrecisionMillis = 1,
                    providerIdentity = "internal"
                )
            )
        )
        SyncRepository.replaceActions(
            run.id,
            listOf(
                SyncAction(
                    runId = run.id,
                    ordinal = 0,
                    type = SyncActionType.COPY,
                    direction = SyncSide.DESTINATION,
                    relativePath = "report.pdf",
                    sourceUri = "file:///storage/docs/report.pdf",
                    targetUri = "rclone://drive/backup/report.pdf",
                    sourceFingerprint = "42:100",
                    comparisonReason = "DESTINATION_MISSING"
                )
            )
        )
        val scanDetails = "DESTINATION\trclone://drive/backup\t/: FileSystemException"
        SyncRepository.transitionRun(
            runId = run.id,
            state = SyncRunState.SAFETY_BLOCKED,
            safetyBlockReason = "SCAN_INCOMPLETE",
            safetyBlockDetails = scanDetails
        )

        TransferDatabase.closeForTests()

        assertEquals(profile, SyncRepository.profile(profile.id))
        val restoredRun = requireNotNull(SyncRepository.run(run.id))
        assertNotNull(restoredRun)
        assertEquals("SCAN_INCOMPLETE", restoredRun.safetyBlockReason)
        assertEquals(scanDetails, restoredRun.safetyBlockDetails)
        assertEquals(1, SyncRepository.actions(run.id).size)
    }

    @Test
    fun databaseEnforcesOneActiveRunPerProfile() {
        val profile = SyncProfile(
            name = "Phone backup",
            sourceUri = "file:///source",
            destinationUri = "file:///destination",
            mode = SyncMode.UPDATE_DESTINATION
        )
        SyncRepository.saveProfile(profile)
        SyncRepository.createRun(
            SyncRun(
                profileId = profile.id,
                trigger = SyncRunTrigger.MANUAL,
                baselineBefore = 0
            )
        )

        assertThrows(Throwable::class.java) {
            SyncRepository.createRun(
                SyncRun(
                    profileId = profile.id,
                    trigger = SyncRunTrigger.SCHEDULED,
                    baselineBefore = 0
                )
            )
        }
    }

    @Test
    fun deleteModifyConflictCanRestoreExistingDestination() {
        val profile = SyncProfile(
            name = "Two way",
            sourceUri = "file:///source",
            destinationUri = "file:///destination",
            mode = SyncMode.TWO_WAY
        )
        SyncRepository.saveProfile(profile)
        val run = SyncRepository.createRun(
            SyncRun(
                profileId = profile.id,
                trigger = SyncRunTrigger.MANUAL,
                baselineBefore = 1
            )
        )
        SyncRepository.replaceActions(
            run.id,
            listOf(
                SyncAction(
                    runId = run.id,
                    ordinal = 0,
                    type = SyncActionType.CONFLICT,
                    direction = SyncSide.DESTINATION,
                    relativePath = "note.txt",
                    sourceUri = "",
                    targetUri = "file:///destination/note.txt",
                    sourceFingerprint = "",
                    targetFingerprint = "f:12:10",
                    comparisonReason = "DELETE_MODIFY_CONFLICT",
                    state = SyncActionState.BLOCKED
                )
            )
        )
        val conflict = SyncRepository.actions(run.id).single()

        SyncRepository.resolveConflict(conflict, "DESTINATION")

        val resolved = SyncRepository.actions(run.id).single()
        assertEquals(SyncActionType.COPY, resolved.type)
        assertEquals(SyncSide.SOURCE, resolved.direction)
        assertEquals("file:///destination/note.txt", resolved.sourceUri)
        assertEquals("file:/source/note.txt", resolved.targetUri)
        assertEquals(SyncActionState.PENDING, resolved.state)
    }

    @Test
    fun deleteProfile_rejectsDeletionWhileRunIsActive() {
        val profile = SyncProfile(
            name = "Active backup",
            sourceUri = "file:///source",
            destinationUri = "file:///destination",
            mode = SyncMode.UPDATE_DESTINATION
        )
        SyncRepository.saveProfile(profile)
        SyncRepository.createRun(
            SyncRun(
                profileId = profile.id,
                trigger = SyncRunTrigger.MANUAL,
                baselineBefore = 0
            )
        )

        assertFalse(SyncRepository.deleteProfile(profile.id))
        assertNotNull(SyncRepository.profile(profile.id))
    }

    @Test
    fun deleteProfile_allowsUnstartedPreviewAndSafetyBlockedRuns() {
        listOf(SyncRunState.PREVIEW_READY, SyncRunState.SAFETY_BLOCKED).forEach { state ->
            val profile = SyncProfile(
                name = "$state backup",
                sourceUri = "file:///source",
                destinationUri = "file:///destination",
                mode = SyncMode.UPDATE_DESTINATION
            )
            SyncRepository.saveProfile(profile)
            val run = SyncRepository.createRun(
                SyncRun(
                    profileId = profile.id,
                    trigger = SyncRunTrigger.MANUAL,
                    state = state,
                    baselineBefore = 0
                )
            )

            assertTrue(SyncRepository.deleteProfile(profile.id))
            assertNull(SyncRepository.profile(profile.id))
            assertNull(SyncRepository.run(run.id))
        }
    }

    @Test
    fun deleteProfile_removesCompletedSyncHistoryWithoutTouchingEndpoints() {
        val profile = SyncProfile(
            name = "Completed backup",
            sourceUri = "file:///source",
            destinationUri = "file:///destination",
            mode = SyncMode.UPDATE_DESTINATION
        )
        SyncRepository.saveProfile(profile)
        val run = SyncRepository.createRun(
            SyncRun(
                profileId = profile.id,
                trigger = SyncRunTrigger.MANUAL,
                state = SyncRunState.COMPLETED,
                baselineBefore = 0
            )
        )
        SyncRepository.addSnapshotEntries(
            listOf(
                SyncSnapshotEntry(
                    profileId = profile.id,
                    generation = 1,
                    side = SyncSide.SOURCE,
                    relativePath = "report.pdf",
                    isDirectory = false,
                    sizeBytes = 42,
                    modifiedAtMillis = 100,
                    modifiedPrecisionMillis = 1,
                    providerIdentity = "internal"
                )
            )
        )
        SyncRepository.replaceActions(
            run.id,
            listOf(
                SyncAction(
                    runId = run.id,
                    ordinal = 0,
                    type = SyncActionType.COPY,
                    direction = SyncSide.DESTINATION,
                    relativePath = "report.pdf",
                    sourceUri = "file:///source/report.pdf",
                    targetUri = "file:///destination/report.pdf",
                    sourceFingerprint = "42:100",
                    comparisonReason = "DESTINATION_MISSING"
                )
            )
        )

        assertTrue(SyncRepository.deleteProfile(profile.id))
        assertNull(SyncRepository.profile(profile.id))
        assertNull(SyncRepository.run(run.id))
        assertTrue(SyncRepository.snapshotEntries(profile.id, 1, SyncSide.SOURCE).isEmpty())
        assertTrue(SyncRepository.actions(run.id).isEmpty())
    }

}
