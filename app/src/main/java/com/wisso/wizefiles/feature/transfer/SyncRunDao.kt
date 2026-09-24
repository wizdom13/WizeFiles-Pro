// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import com.wisso.wizefiles.feature.sync.SyncPlanSummary
import com.wisso.wizefiles.feature.sync.SyncRun
import com.wisso.wizefiles.feature.sync.SyncRunState
import com.wisso.wizefiles.feature.sync.SyncRunStateMachine
import com.wisso.wizefiles.feature.sync.SyncRunTrigger

internal object SyncRunDao {

    fun insertSyncRun(run: SyncRun): SyncRun = withConnection { database ->
        database.prepare(
            """
            INSERT INTO sync_runs(
                id, profile_id, trigger, state, baseline_before, baseline_after,
                transfer_operation_id, safety_block_reason, safety_block_details,
                planned_actions, planned_transfer_bytes, planned_protected_bytes,
                created_at_millis, started_at_millis, completed_at_millis
            ) VALUES (?, ?, ?, ?, ?, ?, NULLIF(?, ''), ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, run.id)
            statement.bindText(2, run.profileId)
            statement.bindText(3, run.trigger.name)
            statement.bindText(4, run.state.name)
            statement.bindLong(5, run.baselineBefore)
            statement.bindLong(6, run.baselineAfter)
            statement.bindText(7, run.transferOperationId)
            statement.bindText(8, run.safetyBlockReason)
            statement.bindText(9, run.safetyBlockDetails)
            statement.bindLong(10, run.plannedActions)
            statement.bindLong(11, run.plannedTransferBytes)
            statement.bindLong(12, run.plannedProtectedBytes)
            statement.bindLong(13, run.createdAtMillis)
            statement.bindLong(14, run.startedAtMillis)
            statement.bindLong(15, run.completedAtMillis)
            statement.step()
        }
        requireNotNull(syncRun(database, run.id))
    }

    fun syncRun(runId: String): SyncRun? = withConnection { syncRun(it, runId) }

    fun syncRuns(profileId: String): List<SyncRun> = withConnection { database ->
        buildList {
            database.prepare(
                """
                SELECT id FROM sync_runs WHERE profile_id=? ORDER BY created_at_millis DESC
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, profileId)
                while (statement.step()) syncRun(database, statement.getText(0))?.let(::add)
            }
        }
    }

    fun syncRunForTransfer(operationId: String): SyncRun? = withConnection { database ->
        database.prepare("SELECT id FROM sync_runs WHERE transfer_operation_id=?").use {
            it.bindText(1, operationId)
            if (it.step()) syncRun(database, it.getText(0)) else null
        }
    }

    fun reconcileInterruptedSyncRuns(): Int = withConnection { database ->
        val runIds = mutableListOf<String>()
        database.prepare(
            """
            SELECT r.id FROM sync_runs r
            LEFT JOIN operations o ON o.id=r.transfer_operation_id
            WHERE r.state='RUNNING' AND (o.state='RECOVERABLE' OR o.id IS NULL)
            """.trimIndent()
        ).use { statement ->
            while (statement.step()) runIds += statement.getText(0)
        }
        database.prepare("UPDATE sync_runs SET state='PAUSED' WHERE id=?").use { statement ->
            runIds.forEach { runId ->
                statement.bindText(1, runId)
                statement.step()
                statement.reset()
            }
        }
        database.prepare("UPDATE sync_actions SET state='PENDING' WHERE run_id=? AND state='RUNNING'").use { statement ->
            runIds.forEach { runId ->
                statement.bindText(1, runId)
                statement.step()
                statement.reset()
            }
        }
        runIds.size
    }

    fun transitionSyncRun(
        runId: String,
        state: SyncRunState,
        safetyBlockReason: String = "",
        safetyBlockDetails: String = "",
        transferOperationId: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): SyncRun = withConnection { database ->
        val current = requireNotNull(syncRun(database, runId))
        SyncRunStateMachine.requireTransition(current.state, state)
        val started = if (current.startedAtMillis == 0L && state == SyncRunState.RUNNING) {
            nowMillis
        } else {
            current.startedAtMillis
        }
        val completed = when {
            state.isTerminal -> nowMillis
            current.state.isTerminal -> 0
            else -> current.completedAtMillis
        }
        database.prepare(
            """
            UPDATE sync_runs SET state=?, safety_block_reason=?, safety_block_details=?,
                transfer_operation_id=COALESCE(NULLIF(?, ''), transfer_operation_id),
                started_at_millis=?, completed_at_millis=? WHERE id=?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, state.name)
            statement.bindText(2, safetyBlockReason)
            statement.bindText(3, safetyBlockDetails)
            statement.bindText(4, transferOperationId)
            statement.bindLong(5, started)
            statement.bindLong(6, completed)
            statement.bindText(7, runId)
            statement.step()
        }
        requireNotNull(syncRun(database, runId))
    }

    fun markSyncRunPreviewReady(runId: String, summary: SyncPlanSummary): SyncRun =
        withConnection { database ->
            val current = requireNotNull(syncRun(database, runId))
            SyncRunStateMachine.requireTransition(current.state, SyncRunState.PREVIEW_READY)
            database.prepare(
                """
                UPDATE sync_runs SET state='PREVIEW_READY', planned_actions=?,
                    planned_transfer_bytes=?, planned_protected_bytes=? WHERE id=?
                """.trimIndent()
            ).use { statement ->
                statement.bindLong(1, summary.copiesToDestination + summary.copiesToSource +
                    summary.updates + summary.moves + summary.protectedItems +
                    summary.deletions + summary.conflicts)
                statement.bindLong(2, summary.transferBytes)
                statement.bindLong(3, summary.protectedBytes)
                statement.bindText(4, runId)
                statement.step()
            }
            requireNotNull(syncRun(database, runId))
        }

    private fun syncRun(database: SQLiteConnection, runId: String): SyncRun? {
        database.prepare(
            """
            SELECT id, profile_id, trigger, state, baseline_before, baseline_after,
                transfer_operation_id, safety_block_reason, safety_block_details,
                planned_actions, planned_transfer_bytes, planned_protected_bytes,
                created_at_millis, started_at_millis, completed_at_millis
            FROM sync_runs WHERE id=?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, runId)
            if (!statement.step()) return null
            return SyncRun(
                id = statement.getText(0),
                profileId = statement.getText(1),
                trigger = SyncRunTrigger.valueOf(statement.getText(2)),
                state = SyncRunState.valueOf(statement.getText(3)),
                baselineBefore = statement.getLong(4),
                baselineAfter = statement.getLong(5),
                transferOperationId = statement.getText(6),
                safetyBlockReason = statement.getText(7),
                safetyBlockDetails = statement.getText(8),
                plannedActions = statement.getLong(9),
                plannedTransferBytes = statement.getLong(10),
                plannedProtectedBytes = statement.getLong(11),
                createdAtMillis = statement.getLong(12),
                startedAtMillis = statement.getLong(13),
                completedAtMillis = statement.getLong(14)
            )
        }
    }

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        TransferDatabaseConnection.withConnection(block)
}
