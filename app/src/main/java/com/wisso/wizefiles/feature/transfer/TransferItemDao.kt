// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object TransferItemDao {

    fun beginItem(
        operationId: String,
        sourceUri: String,
        targetUri: String,
        relativePath: String,
        isDirectory: Boolean,
        sizeBytes: Long,
        modifiedMillis: Long,
        sourceFingerprint: String
    ): TransferItemRecord = withConnection { database ->
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            var item = item(database, operationId, sourceUri, targetUri)
            if (item == null) {
                database.prepare(
                    """
                    INSERT INTO operation_items(
                        operation_id, ordinal, source_uri, target_uri, relative_path,
                        is_directory, size_bytes, modified_millis, source_fingerprint, state
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """.trimIndent()
                ).use { statement ->
                    statement.bindText(1, operationId)
                    statement.bindLong(2, nextItemOrdinal(database, operationId))
                    statement.bindText(3, sourceUri)
                    statement.bindText(4, targetUri)
                    statement.bindText(5, relativePath)
                    statement.bindInt(6, if (isDirectory) 1 else 0)
                    statement.bindLong(7, sizeBytes.coerceAtLeast(0))
                    statement.bindLong(8, modifiedMillis.coerceAtLeast(0))
                    statement.bindText(9, sourceFingerprint)
                    statement.step()
                }
                item = requireNotNull(item(database, operationId, sourceUri, targetUri))
            }
            val currentItem = requireNotNull(item)
            if (
                currentItem.state != TransferItemState.COPIED &&
                currentItem.state != TransferItemState.SKIPPED
            ) {
                database.prepare(
                    """
                    UPDATE operation_items SET state='ACTIVE', bytes_completed=0,
                        attempt_count=attempt_count+1, error_category='', error_message=''
                    WHERE id=?
                    """.trimIndent()
                ).use { statement ->
                    statement.bindLong(1, currentItem.id)
                    statement.step()
                }
            }
            database.execSQL("COMMIT")
            requireNotNull(item(database, operationId, sourceUri, targetUri))
        } catch (throwable: Throwable) {
            runCatching { database.execSQL("ROLLBACK") }
            throw throwable
        }
    }

    fun checkpoint(checkpoint: TransferProgressCheckpoint) = withConnection { database ->
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            database.prepare(
                "UPDATE operation_items SET bytes_completed=? WHERE id=? AND operation_id=?"
            ).use { statement ->
                statement.bindLong(1, checkpoint.itemBytesCompleted.coerceAtLeast(0))
                statement.bindLong(2, checkpoint.itemId)
                statement.bindText(3, checkpoint.operationId)
                statement.step()
            }
            database.prepare(
                """
                UPDATE operations SET transferred_bytes=?, current_item=?,
                    speed_bytes_per_second=?, eta_seconds=?, updated_at_millis=?
                WHERE id=?
                """.trimIndent()
            ).use { statement ->
                statement.bindLong(1, checkpoint.operationBytesCompleted.coerceAtLeast(0))
                statement.bindText(2, checkpoint.currentItem)
                statement.bindLong(3, checkpoint.speedBytesPerSecond.coerceAtLeast(0))
                statement.bindLong(4, checkpoint.etaSeconds)
                statement.bindLong(5, checkpoint.updatedAtMillis)
                statement.bindText(6, checkpoint.operationId)
                statement.step()
            }
            database.execSQL("COMMIT")
        } catch (throwable: Throwable) {
            runCatching { database.execSQL("ROLLBACK") }
            throw throwable
        }
    }

    fun setTemporaryTarget(itemId: Long, temporaryTargetUri: String) = withConnection { database ->
        database.prepare(
            "UPDATE operation_items SET temporary_target_uri=? WHERE id=?"
        ).use { statement ->
            statement.bindText(1, temporaryTargetUri)
            statement.bindLong(2, itemId)
            statement.step()
        }
    }

    fun completeItem(itemId: Long, resultUri: String, nowMillis: Long = System.currentTimeMillis()) =
        finishItem(itemId, TransferItemState.COPIED, resultUri, "", "", nowMillis)

    fun skipItem(itemId: Long, nowMillis: Long = System.currentTimeMillis()) =
        finishItem(itemId, TransferItemState.SKIPPED, "", "", "", nowMillis)

    fun failItem(
        itemId: Long,
        errorCategory: String,
        errorMessage: String,
        nowMillis: Long = System.currentTimeMillis()
    ) = finishItem(
        itemId,
        TransferItemState.FAILED,
        "",
        errorCategory,
        errorMessage,
        nowMillis
    )

    fun items(operationId: String): List<TransferItemRecord> = withConnection { database ->
        buildList {
            database.prepare(
                "SELECT ${itemColumns()} FROM operation_items WHERE operation_id=? ORDER BY ordinal"
            ).use { statement ->
                statement.bindText(1, operationId)
                while (statement.step()) add(statement.toTransferItemRecord())
            }
        }
    }

    fun retryFailedItems(operationId: String): Int = withConnection { database ->
        var count = 0
        database.prepare(
            """
            UPDATE operation_items SET state='PENDING', bytes_completed=0,
                error_category='', error_message='', completed_at_millis=0
            WHERE operation_id=? AND state='FAILED' RETURNING id
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, operationId)
            while (statement.step()) count++
        }
        count
    }

    private fun item(
        database: SQLiteConnection,
        operationId: String,
        sourceUri: String,
        targetUri: String
    ): TransferItemRecord? {
        database.prepare(
            """
            SELECT ${itemColumns()} FROM operation_items
            WHERE operation_id=? AND source_uri=? AND target_uri=?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, operationId)
            statement.bindText(2, sourceUri)
            statement.bindText(3, targetUri)
            return if (statement.step()) statement.toTransferItemRecord() else null
        }
    }

    private fun item(database: SQLiteConnection, itemId: Long): TransferItemRecord? {
        database.prepare("SELECT ${itemColumns()} FROM operation_items WHERE id=?").use { statement ->
            statement.bindLong(1, itemId)
            return if (statement.step()) statement.toTransferItemRecord() else null
        }
    }

    private fun itemColumns(): String =
        "id, operation_id, ordinal, source_uri, target_uri, relative_path, is_directory, " +
            "size_bytes, modified_millis, source_fingerprint, state, bytes_completed, " +
            "attempt_count, temporary_target_uri, final_result_uri, error_category, " +
            "error_message, completed_at_millis"

    private fun androidx.sqlite.SQLiteStatement.toTransferItemRecord() = TransferItemRecord(
        id = getLong(0),
        operationId = getText(1),
        ordinal = getLong(2),
        sourceUri = getText(3),
        targetUri = getText(4),
        relativePath = getText(5),
        isDirectory = getInt(6) != 0,
        sizeBytes = getLong(7),
        modifiedMillis = getLong(8),
        sourceFingerprint = getText(9),
        state = TransferItemState.valueOf(getText(10)),
        bytesCompleted = getLong(11),
        attemptCount = getInt(12),
        temporaryTargetUri = getText(13),
        finalResultUri = getText(14),
        errorCategory = getText(15),
        errorMessage = getText(16),
        completedAtMillis = getLong(17)
    )

    private fun nextItemOrdinal(database: SQLiteConnection, operationId: String): Long {
        database.prepare(
            "SELECT COALESCE(MAX(ordinal), -1) + 1 FROM operation_items WHERE operation_id=?"
        ).use { statement ->
            statement.bindText(1, operationId)
            check(statement.step())
            return statement.getLong(0)
        }
    }

    private fun finishItem(
        itemId: Long,
        state: TransferItemState,
        resultUri: String,
        errorCategory: String,
        errorMessage: String,
        nowMillis: Long
    ) = withConnection { database ->
        val item = item(database, itemId) ?: return@withConnection
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            database.prepare(
                """
                UPDATE operation_items SET state=?, bytes_completed=CASE WHEN ?='COPIED'
                    THEN size_bytes ELSE bytes_completed END, final_result_uri=?,
                    error_category=?, error_message=?, completed_at_millis=? WHERE id=?
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, state.name)
                statement.bindText(2, state.name)
                statement.bindText(3, resultUri)
                statement.bindText(4, errorCategory)
                statement.bindText(5, errorMessage)
                statement.bindLong(6, nowMillis)
                statement.bindLong(7, itemId)
                statement.step()
            }
            database.prepare(
                """
                UPDATE operations SET
                    completed_items=(SELECT COUNT(*) FROM operation_items WHERE operation_id=? AND state='COPIED'),
                    failed_items=(SELECT COUNT(*) FROM operation_items WHERE operation_id=? AND state='FAILED'),
                    skipped_items=(SELECT COUNT(*) FROM operation_items WHERE operation_id=? AND state='SKIPPED'),
                    transferred_bytes=(SELECT COALESCE(SUM(bytes_completed), 0) FROM operation_items WHERE operation_id=?),
                    updated_at_millis=? WHERE id=?
                """.trimIndent()
            ).use { statement ->
                repeat(4) { index -> statement.bindText(index + 1, item.operationId) }
                statement.bindLong(5, nowMillis)
                statement.bindText(6, item.operationId)
                statement.step()
            }
            database.execSQL("COMMIT")
        } catch (throwable: Throwable) {
            runCatching { database.execSQL("ROLLBACK") }
            throw throwable
        }
    }

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        TransferDatabaseConnection.withConnection(block)
}
