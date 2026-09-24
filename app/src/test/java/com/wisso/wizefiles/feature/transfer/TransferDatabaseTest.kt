package com.wisso.wizefiles.feature.transfer

import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecretRegistry
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecrets
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferDatabaseTest {
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
    fun `operation and versioned provider paths survive database reopen`() {
        val spec = TransferOperationSpec(
            id = "persistent-copy",
            type = TransferOperationType.COPY,
            sourceUris = listOf("smb://server/share/a.txt", "rclone://drive/b.txt"),
            destinationUri = "content://provider/tree/primary%3ADownload",
            createdAtMillis = 100
        )
        TransferDatabase.insertOperation(spec)
        TransferDatabase.closeForTests()

        val restored = TransferDatabase.operation(spec.id)
        assertNotNull(restored)
        assertEquals(TRANSFER_PATH_SCHEMA_VERSION, restored?.pathSchemaVersion)
        assertEquals(spec.destinationUri, restored?.destinationUri)
        assertEquals(spec.sourceUris, TransferDatabase.sourceUris(spec.id))
    }

    @Test
    fun `process recovery preserves completed work and resets only active items`() {
        val spec = TransferOperationSpec(
            id = "recoverable-move",
            type = TransferOperationType.MOVE,
            sourceUris = listOf("file:///source"),
            destinationUri = "sftp://host/target",
            createdAtMillis = 200
        )
        TransferDatabase.insertOperation(spec)
        TransferDatabase.transition(spec.id, TransferOperationState.PLANNING, nowMillis = 201)
        TransferDatabase.transition(spec.id, TransferOperationState.RUNNING, nowMillis = 202)

        assertEquals(1, TransferDatabase.recoverInterrupted(nowMillis = 300))
        val restored = requireNotNull(TransferDatabase.operation(spec.id))
        assertEquals(TransferOperationState.RECOVERABLE, restored.state)
        assertEquals("PROCESS_TERMINATED", restored.recoveryReason)
    }

    @Test
    fun `only queued operations can be removed`() {
        val queued = TransferOperationSpec(
            id = "queued",
            type = TransferOperationType.COPY,
            sourceUris = listOf("file:///a"),
            destinationUri = "file:///b"
        )
        val running = queued.copy(id = "running")
        TransferDatabase.insertOperation(queued)
        TransferDatabase.insertOperation(running)
        TransferDatabase.transition(running.id, TransferOperationState.RUNNING)

        assertTrue(TransferDatabase.deleteQueued(queued.id))
        assertFalse(TransferDatabase.deleteQueued(running.id))
    }

    @Test
    fun `section history cleanup preserves other terminal states`() {
        val completed = TransferOperationSpec(
            id = "completed",
            type = TransferOperationType.COPY,
            sourceUris = listOf("file:///completed"),
            destinationUri = "file:///target"
        )
        val failed = completed.copy(id = "failed", sourceUris = listOf("file:///failed"))
        val cancelled = completed.copy(
            id = "cancelled",
            sourceUris = listOf("file:///cancelled")
        )
        TransferDatabase.insertOperation(completed)
        TransferDatabase.insertOperation(failed)
        TransferDatabase.insertOperation(cancelled)
        TransferDatabase.transition(completed.id, TransferOperationState.RUNNING)
        TransferDatabase.transition(completed.id, TransferOperationState.COMPLETED)
        TransferDatabase.transition(failed.id, TransferOperationState.RUNNING)
        TransferDatabase.transition(failed.id, TransferOperationState.FAILED)
        TransferDatabase.transition(cancelled.id, TransferOperationState.CANCELLED)

        assertEquals(1, TransferRepository.clearCompletedHistory())
        assertNull(TransferDatabase.operation(completed.id))
        assertNotNull(TransferDatabase.operation(failed.id))
        assertNotNull(TransferDatabase.operation(cancelled.id))

        assertEquals(2, TransferRepository.clearFailedHistory())
        assertNull(TransferDatabase.operation(failed.id))
        assertNull(TransferDatabase.operation(cancelled.id))
    }

    @Test
    fun `completed item checkpoint is idempotent and is not restarted`() {
        val spec = TransferOperationSpec(
            id = "checkpoint-copy",
            type = TransferOperationType.COPY,
            sourceUris = listOf("file:///source/a.bin"),
            destinationUri = "smb://server/share"
        )
        TransferDatabase.insertOperation(spec)
        val item = TransferDatabase.beginItem(
            operationId = spec.id,
            sourceUri = spec.sourceUris.single(),
            targetUri = "smb://server/share/a.bin",
            relativePath = "a.bin",
            isDirectory = false,
            sizeBytes = 4_096,
            modifiedMillis = 123,
            sourceFingerprint = "4096:123"
        )
        TransferDatabase.completeItem(item.id, "smb://server/share/a.bin", nowMillis = 400)

        val restored = TransferDatabase.beginItem(
            operationId = spec.id,
            sourceUri = spec.sourceUris.single(),
            targetUri = "smb://server/share/a.bin",
            relativePath = "a.bin",
            isDirectory = false,
            sizeBytes = 4_096,
            modifiedMillis = 123,
            sourceFingerprint = "4096:123"
        )

        assertEquals(TransferItemState.COPIED, restored.state)
        assertEquals(4_096L, restored.bytesCompleted)
        assertEquals(1, restored.attemptCount)
    }

    @Test
    fun `retry failed items preserves completed and skipped results`() {
        val spec = TransferOperationSpec(
            id = "retry-items",
            type = TransferOperationType.COPY,
            sourceUris = listOf("file:///source"),
            destinationUri = "file:///target"
        )
        TransferDatabase.insertOperation(spec)
        fun item(name: String) = TransferDatabase.beginItem(
            operationId = spec.id,
            sourceUri = "file:///source/$name",
            targetUri = "file:///target/$name",
            relativePath = name,
            isDirectory = false,
            sizeBytes = 10,
            modifiedMillis = 1,
            sourceFingerprint = "10:1"
        )
        val copied = item("copied")
        val skipped = item("skipped")
        val failed = item("failed")
        TransferDatabase.completeItem(copied.id, copied.targetUri)
        TransferDatabase.skipItem(skipped.id)
        TransferDatabase.failItem(failed.id, "IOException", "network")

        assertEquals(1, TransferDatabase.retryFailedItems(spec.id))
        val states = TransferDatabase.items(spec.id).associate { it.relativePath to it.state }
        assertEquals(TransferItemState.COPIED, states["copied"])
        assertEquals(TransferItemState.SKIPPED, states["skipped"])
        assertEquals(TransferItemState.PENDING, states["failed"])
    }

    @Test
    fun `unresolved user decision survives process recreation`() {
        val spec = TransferOperationSpec(
            id = "waiting-conflict",
            type = TransferOperationType.COPY,
            sourceUris = listOf("file:///source/report.pdf"),
            destinationUri = "file:///target"
        )
        TransferDatabase.insertOperation(spec)
        TransferDatabase.transition(spec.id, TransferOperationState.RUNNING)
        val decisionId = TransferRepository.beginDecision(
            spec.id,
            0,
            "TARGET_EXISTS",
            "report.pdf",
            "replace,rename,skip"
        )

        val waiting = requireNotNull(TransferDatabase.operation(spec.id))
        assertEquals(TransferOperationState.WAITING_FOR_USER, waiting.state)
        assertEquals(listOf(decisionId), TransferDatabase.pendingDecisions(spec.id).map { it.id })

        TransferRepository.resolveDecision(spec.id, decisionId, "skip", false)
        assertTrue(TransferDatabase.pendingDecisions(spec.id).isEmpty())
        assertEquals(
            TransferOperationState.RUNNING,
            TransferDatabase.operation(spec.id)?.state
        )
    }

    @Test
    fun `deleting package operation history removes metadata and signing secrets`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val metadataDirectories = mapOf(
            TransferOperationType.APK_SIGN to "apk-signing-operations",
            TransferOperationType.AAB_SIGN to "aab-signing-operations",
            TransferOperationType.APKS_SIGN to "apks-signing-operations",
            TransferOperationType.XAPK_SIGN to "xapk-signing-operations",
            TransferOperationType.APKM_IMPORT to "apkm-import-operations"
        )
        val signingTypes = setOf(
            TransferOperationType.APK_SIGN,
            TransferOperationType.AAB_SIGN,
            TransferOperationType.APKS_SIGN,
            TransferOperationType.XAPK_SIGN
        )
        val operationIds = mutableListOf<String>()
        try {
            metadataDirectories.forEach { (type, directoryName) ->
                val operationId = "history-${type.name.lowercase()}"
                operationIds += operationId
                TransferDatabase.insertOperation(
                    TransferOperationSpec(
                        id = operationId,
                        type = type,
                        sourceUris = listOf("file:///source"),
                        destinationUri = "file:///target"
                    )
                )
                TransferDatabase.transition(operationId, TransferOperationState.RUNNING)
                TransferDatabase.transition(operationId, TransferOperationState.FAILED)

                val directory = File(context.noBackupFilesDir, directoryName).apply { mkdirs() }
                val metadata = File(directory, "$operationId.json").apply { writeText("{}") }
                val temporary = File(directory, "$operationId.json.tmp").apply { writeText("{}") }
                if (type in signingTypes) {
                    ApkSigningSecretRegistry.put(
                        operationId,
                        ApkSigningSecrets(charArrayOf('s'))
                    )
                    assertTrue(ApkSigningSecretRegistry.has(operationId))
                }

                assertTrue(TransferRepository.deleteHistory(operationId))
                assertFalse(metadata.exists())
                assertFalse(temporary.exists())
                assertFalse(ApkSigningSecretRegistry.has(operationId))
            }
        } finally {
            operationIds.forEach(ApkSigningSecretRegistry::clear)
            metadataDirectories.values.forEach { directoryName ->
                File(context.noBackupFilesDir, directoryName).deleteRecursively()
            }
        }
    }

}
