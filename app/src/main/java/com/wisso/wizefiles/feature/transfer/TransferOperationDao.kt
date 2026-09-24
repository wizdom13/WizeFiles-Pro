package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object TransferOperationDao {

    fun insertOperation(spec: TransferOperationSpec): TransferOperationRecord = withConnection { database ->
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val queuePosition = nextQueuePosition(database)
            database.prepare(
                """
                INSERT INTO operations(
                    id, type, state, destination_uri, path_schema_version, queue_position,
                    created_at_millis, updated_at_millis
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, spec.id)
                statement.bindText(2, spec.type.name)
                statement.bindText(3, TransferOperationState.QUEUED.name)
                statement.bindText(4, spec.destinationUri)
                statement.bindInt(5, spec.pathSchemaVersion)
                statement.bindLong(6, queuePosition)
                statement.bindLong(7, spec.createdAtMillis)
                statement.bindLong(8, spec.createdAtMillis)
                statement.step()
            }
            database.prepare(
                "INSERT INTO operation_sources(operation_id, ordinal, source_uri) VALUES (?, ?, ?)"
            ).use { statement ->
                spec.sourceUris.forEachIndexed { index, sourceUri ->
                    statement.bindText(1, spec.id)
                    statement.bindLong(2, index.toLong())
                    statement.bindText(3, sourceUri)
                    statement.step()
                    statement.reset()
                }
            }
            database.execSQL("COMMIT")
            requireNotNull(operation(database, spec.id))
        } catch (throwable: Throwable) {
            runCatching { database.execSQL("ROLLBACK") }
            throw throwable
        }
    }

    fun operation(id: String): TransferOperationRecord? = withConnection { operation(it, id) }

    fun sourceUris(operationId: String): List<String> = withConnection { database ->
        buildList {
            database.prepare(
                "SELECT source_uri FROM operation_sources WHERE operation_id=? ORDER BY ordinal"
            ).use { statement ->
                statement.bindText(1, operationId)
                while (statement.step()) add(statement.getText(0))
            }
        }
    }

    fun operations(states: Set<TransferOperationState>? = null): List<TransferOperationRecord> =
        withConnection { database ->
            val records = mutableListOf<TransferOperationRecord>()
            database.prepare(
                "SELECT ${operationColumns()} FROM operations ORDER BY queue_position, created_at_millis"
            ).use { statement ->
                while (statement.step()) {
                    val record = statement.toOperationRecord()
                    if (states == null || record.state in states) records += record
                }
            }
            records
        }

    fun transition(
        operationId: String,
        state: TransferOperationState,
        reason: String = "",
        errorCategory: String = "",
        errorMessage: String = "",
        requiresUserAction: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): TransferOperationRecord = withConnection { database ->
        val current = requireNotNull(operation(database, operationId))
        TransferStateMachine.requireTransition(current.state, state)
        val startedAt = if (
            current.startedAtMillis == 0L &&
            (state == TransferOperationState.PLANNING || state == TransferOperationState.RUNNING)
        ) nowMillis else current.startedAtMillis
        val completedAt = if (state.isTerminal) nowMillis else 0L
        database.prepare(
            """
            UPDATE operations SET state=?, started_at_millis=?, updated_at_millis=?,
                completed_at_millis=?, recovery_reason=?, last_error_category=?,
                last_error_message=?, requires_user_action=? WHERE id=?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, state.name)
            statement.bindLong(2, startedAt)
            statement.bindLong(3, nowMillis)
            statement.bindLong(4, completedAt)
            statement.bindText(5, reason)
            statement.bindText(6, errorCategory)
            statement.bindText(7, errorMessage)
            statement.bindInt(8, if (requiresUserAction) 1 else 0)
            statement.bindText(9, operationId)
            statement.step()
        }
        requireNotNull(operation(database, operationId))
    }

    fun reorderQueued(operationIds: List<String>) = withConnection { database ->
        if (operationIds.isEmpty()) return@withConnection
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            database.prepare(
                "UPDATE operations SET queue_position=?, updated_at_millis=? " +
                    "WHERE id=? AND state='QUEUED'"
            ).use { statement ->
                val now = System.currentTimeMillis()
                operationIds.forEachIndexed { index, operationId ->
                    statement.bindLong(1, index.toLong())
                    statement.bindLong(2, now)
                    statement.bindText(3, operationId)
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

    fun deleteQueued(operationId: String): Boolean = withConnection { database ->
        val current = operation(database, operationId) ?: return@withConnection false
        if (current.state != TransferOperationState.QUEUED) return@withConnection false
        database.prepare("DELETE FROM operations WHERE id=?").use { statement ->
            statement.bindText(1, operationId)
            statement.step()
        }
        true
    }

    fun recoverInterrupted(nowMillis: Long = System.currentTimeMillis()): Int = withConnection { database ->
        var count = 0
        database.prepare(
            """
            UPDATE operations SET state='RECOVERABLE', recovery_reason='PROCESS_TERMINATED',
                updated_at_millis=?, requires_user_action=0
            WHERE state IN ('PLANNING', 'RUNNING', 'PAUSE_REQUESTED')
            RETURNING id
            """.trimIndent()
        ).use { statement ->
            statement.bindLong(1, nowMillis)
            while (statement.step()) count++
        }
        database.prepare(
            "UPDATE operation_items SET state='PENDING' WHERE state='ACTIVE'"
        ).use { it.step() }
        count
    }

    fun updatePlanSummary(operationId: String, totalItems: Long, totalBytes: Long) =
        withConnection { database ->
            database.prepare(
                """
                UPDATE operations SET total_items=?, total_bytes=?, updated_at_millis=? WHERE id=?
                """.trimIndent()
            ).use { statement ->
                statement.bindLong(1, totalItems.coerceAtLeast(0))
                statement.bindLong(2, totalBytes.coerceAtLeast(0))
                statement.bindLong(3, System.currentTimeMillis())
                statement.bindText(4, operationId)
                statement.step()
            }
        }

    fun deleteHistory(operationId: String): Boolean = withConnection { database ->
        val operation = operation(database, operationId) ?: return@withConnection false
        if (!operation.state.isTerminal) return@withConnection false
        database.prepare("DELETE FROM operations WHERE id=?").use { statement ->
            statement.bindText(1, operationId)
            statement.step()
        }
        true
    }

    fun pruneHistory(
        olderThanMillis: Long,
        keepLatest: Int = 500
    ): Int = withConnection { database ->
        val removable = mutableListOf<String>()
        database.prepare(
            """
            SELECT id, completed_at_millis FROM operations
            WHERE state IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS', 'CANCELLED')
            ORDER BY completed_at_millis DESC
            """.trimIndent()
        ).use { statement ->
            var index = 0
            while (statement.step()) {
                val completedAt = statement.getLong(1)
                if (index >= keepLatest || completedAt < olderThanMillis) {
                    removable += statement.getText(0)
                }
                index++
            }
        }
        database.prepare("DELETE FROM operations WHERE id=?").use { statement ->
            removable.forEach { operationId ->
                statement.bindText(1, operationId)
                statement.step()
                statement.reset()
            }
        }
        removable.size
    }

    private fun operation(database: SQLiteConnection, id: String): TransferOperationRecord? {
        database.prepare("SELECT ${operationColumns()} FROM operations WHERE id=?").use { statement ->
            statement.bindText(1, id)
            return if (statement.step()) statement.toOperationRecord() else null
        }
    }

    private fun operationColumns(): String =
        "id, type, state, destination_uri, path_schema_version, queue_position, " +
            "created_at_millis, started_at_millis, updated_at_millis, completed_at_millis, " +
            "total_items, completed_items, failed_items, skipped_items, total_bytes, " +
            "transferred_bytes, current_item, last_error_category, last_error_message, " +
                "requires_user_action, recovery_reason, speed_bytes_per_second, eta_seconds"

    private fun androidx.sqlite.SQLiteStatement.toOperationRecord() = TransferOperationRecord(
        id = getText(0),
        type = TransferOperationType.valueOf(getText(1)),
        state = TransferOperationState.valueOf(getText(2)),
        destinationUri = getText(3),
        pathSchemaVersion = getInt(4),
        queuePosition = getLong(5),
        createdAtMillis = getLong(6),
        startedAtMillis = getLong(7),
        updatedAtMillis = getLong(8),
        completedAtMillis = getLong(9),
        totalItems = getLong(10),
        completedItems = getLong(11),
        failedItems = getLong(12),
        skippedItems = getLong(13),
        totalBytes = getLong(14),
        transferredBytes = getLong(15),
        currentItem = getText(16),
        lastErrorCategory = getText(17),
        lastErrorMessage = getText(18),
        requiresUserAction = getInt(19) != 0,
        recoveryReason = getText(20),
        speedBytesPerSecond = getLong(21),
        etaSeconds = getLong(22)
    )

    private fun nextQueuePosition(database: SQLiteConnection): Long {
        database.prepare("SELECT COALESCE(MAX(queue_position), -1) + 1 FROM operations").use {
            check(it.step())
            return it.getLong(0)
        }
    }

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        TransferDatabaseConnection.withConnection(block)
}
