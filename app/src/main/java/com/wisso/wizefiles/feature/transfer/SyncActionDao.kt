package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.wisso.wizefiles.feature.sync.SyncAction
import com.wisso.wizefiles.feature.sync.SyncActionState
import com.wisso.wizefiles.feature.sync.SyncActionType
import com.wisso.wizefiles.feature.sync.SyncSide

internal object SyncActionDao {

    fun resetRunningSyncActions(runId: String) = withConnection { database ->
        database.prepare("UPDATE sync_actions SET state='PENDING' WHERE run_id=? AND state='RUNNING'").use {
            it.bindText(1, runId)
            it.step()
        }
    }

    fun retryFailedSyncActions(runId: String) = withConnection { database ->
        database.prepare(
            "UPDATE sync_actions SET state='PENDING', error_message='' WHERE run_id=? AND state='FAILED'"
        ).use {
            it.bindText(1, runId)
            it.step()
        }
    }

    fun replaceSyncActions(runId: String, actions: List<SyncAction>) = withConnection { database ->
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            database.prepare("DELETE FROM sync_actions WHERE run_id=?").use {
                it.bindText(1, runId)
                it.step()
            }
            database.prepare(
                """
                INSERT INTO sync_actions(
                    run_id, ordinal, action_type, direction, relative_path,
                    source_uri, target_uri, source_fingerprint, target_fingerprint,
                    comparison_reason, state, transfer_item_id, protected_result_uri,
                    error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, 0), ?, ?)
                """.trimIndent()
            ).use { statement ->
                actions.forEach { action ->
                    require(action.runId == runId)
                    statement.bindText(1, runId)
                    statement.bindLong(2, action.ordinal)
                    statement.bindText(3, action.type.name)
                    statement.bindText(4, action.direction.name)
                    statement.bindText(5, action.relativePath)
                    statement.bindText(6, action.sourceUri)
                    statement.bindText(7, action.targetUri)
                    statement.bindText(8, action.sourceFingerprint)
                    statement.bindText(9, action.targetFingerprint)
                    statement.bindText(10, action.comparisonReason)
                    statement.bindText(11, action.state.name)
                    statement.bindLong(12, action.transferItemId)
                    statement.bindText(13, action.protectedResultUri)
                    statement.bindText(14, action.errorMessage)
                    statement.step()
                    statement.reset()
                }
            }
            database.execSQL("COMMIT")
        } catch (throwable: Throwable) {
            runCatching { database.execSQL("ROLLBACK") }
            throw throwable
        }
    }

    fun syncActions(runId: String): List<SyncAction> = withConnection { database ->
        buildList {
            database.prepare(
                """
                SELECT id, run_id, ordinal, action_type, direction, relative_path,
                    source_uri, target_uri, source_fingerprint, target_fingerprint,
                    comparison_reason, state, COALESCE(transfer_item_id, 0),
                    protected_result_uri, error_message
                FROM sync_actions WHERE run_id=? ORDER BY ordinal
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, runId)
                while (statement.step()) {
                    add(
                        SyncAction(
                            id = statement.getLong(0),
                            runId = statement.getText(1),
                            ordinal = statement.getLong(2),
                            type = SyncActionType.valueOf(statement.getText(3)),
                            direction = SyncSide.valueOf(statement.getText(4)),
                            relativePath = statement.getText(5),
                            sourceUri = statement.getText(6),
                            targetUri = statement.getText(7),
                            sourceFingerprint = statement.getText(8),
                            targetFingerprint = statement.getText(9),
                            comparisonReason = statement.getText(10),
                            state = SyncActionState.valueOf(statement.getText(11)),
                            transferItemId = statement.getLong(12),
                            protectedResultUri = statement.getText(13),
                            errorMessage = statement.getText(14)
                        )
                    )
                }
            }
        }
    }

    fun updateSyncAction(
        actionId: Long,
        state: SyncActionState,
        transferItemId: Long = 0,
        protectedResultUri: String = "",
        errorMessage: String = ""
    ) = withConnection { database ->
        database.prepare(
            """
            UPDATE sync_actions SET state=?,
                transfer_item_id=COALESCE(NULLIF(?, 0), transfer_item_id),
                protected_result_uri=CASE WHEN ?='' THEN protected_result_uri ELSE ? END,
                error_message=? WHERE id=?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, state.name)
            statement.bindLong(2, transferItemId)
            statement.bindText(3, protectedResultUri)
            statement.bindText(4, protectedResultUri)
            statement.bindText(5, errorMessage)
            statement.bindLong(6, actionId)
            statement.step()
        }
    }

    fun rewriteSyncConflict(action: SyncAction) = withConnection { database ->
        database.prepare(
            """
            UPDATE sync_actions SET action_type=?, direction=?, source_uri=?, target_uri=?,
                source_fingerprint=?, target_fingerprint=?, comparison_reason=?, state=?
            WHERE id=? AND action_type='CONFLICT'
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, action.type.name)
            statement.bindText(2, action.direction.name)
            statement.bindText(3, action.sourceUri)
            statement.bindText(4, action.targetUri)
            statement.bindText(5, action.sourceFingerprint)
            statement.bindText(6, action.targetFingerprint)
            statement.bindText(7, action.comparisonReason)
            statement.bindText(8, action.state.name)
            statement.bindLong(9, action.id)
            statement.step()
        }
    }

    fun skipUnusedConflictProtection(
        runId: String,
        relativePath: String,
        retainedSide: SyncSide?
    ) = withConnection { database ->
        val sql = if (retainedSide == null) {
            """
            UPDATE sync_actions SET state='SKIPPED' WHERE run_id=? AND relative_path=?
                AND action_type='PROTECT' AND comparison_reason='VERSION_BEFORE_CONFLICT'
            """.trimIndent()
        } else {
            """
            UPDATE sync_actions SET state='SKIPPED' WHERE run_id=? AND relative_path=?
                AND action_type='PROTECT' AND comparison_reason='VERSION_BEFORE_CONFLICT'
                AND direction<>?
            """.trimIndent()
        }
        database.prepare(sql).use { statement ->
            statement.bindText(1, runId)
            statement.bindText(2, relativePath)
            retainedSide?.let { statement.bindText(3, it.name) }
            statement.step()
        }
    }

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        TransferDatabaseConnection.withConnection(block)
}
