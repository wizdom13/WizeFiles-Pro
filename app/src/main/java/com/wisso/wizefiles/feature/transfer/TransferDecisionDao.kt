package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection

internal object TransferDecisionDao {

    fun insertDecision(
        operationId: String,
        itemId: Long,
        type: String,
        payload: String,
        availableResponses: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long = withConnection { database ->
        database.prepare(
            """
            INSERT INTO pending_decisions(
                operation_id, item_id, decision_type, payload, available_responses,
                created_at_millis
            ) VALUES (?, NULLIF(?, 0), ?, ?, ?, ?) RETURNING id
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, operationId)
            statement.bindLong(2, itemId)
            statement.bindText(3, type)
            statement.bindText(4, payload)
            statement.bindText(5, availableResponses)
            statement.bindLong(6, nowMillis)
            check(statement.step())
            statement.getLong(0)
        }
    }

    fun pendingDecisions(operationId: String): List<PendingTransferDecision> =
        withConnection { database ->
            buildList {
                database.prepare(
                    """
                    SELECT id, operation_id, COALESCE(item_id, 0), decision_type, payload,
                        available_responses, created_at_millis FROM pending_decisions
                    WHERE operation_id=? AND selected_response='' ORDER BY id
                    """.trimIndent()
                ).use { statement ->
                    statement.bindText(1, operationId)
                    while (statement.step()) {
                        add(
                            PendingTransferDecision(
                                id = statement.getLong(0),
                                operationId = statement.getText(1),
                                itemId = statement.getLong(2),
                                type = statement.getText(3),
                                payload = statement.getText(4),
                                availableResponses = statement.getText(5),
                                createdAtMillis = statement.getLong(6)
                            )
                        )
                    }
                }
            }
        }

    fun resolveDecision(decisionId: Long, response: String, applyToAll: Boolean) =
        withConnection { database ->
            database.prepare(
                "UPDATE pending_decisions SET selected_response=?, apply_to_all=? WHERE id=?"
            ).use { statement ->
                statement.bindText(1, response)
                statement.bindInt(2, if (applyToAll) 1 else 0)
                statement.bindLong(3, decisionId)
                statement.step()
            }
        }

    fun abandonPendingDecisions(operationId: String) = withConnection { database ->
        database.prepare("DELETE FROM pending_decisions WHERE operation_id=? AND selected_response=''").use {
            it.bindText(1, operationId)
            it.step()
        }
    }

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        TransferDatabaseConnection.withConnection(block)
}
