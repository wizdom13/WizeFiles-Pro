package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object TransferDatabaseSchema {
    const val CURRENT_VERSION = 4

    fun configure(database: SQLiteConnection) {
        val previousVersion = readSchemaVersion(database)
        createSchema(database)
        migrate(database, previousVersion)
        database.execSQL("PRAGMA user_version=$CURRENT_VERSION")
    }

    private fun migrate(database: SQLiteConnection, previousVersion: Int) {
        if (previousVersion in 1 until CURRENT_VERSION) {
            if (!hasColumn(database, "operations", "speed_bytes_per_second")) {
                database.execSQL(
                    "ALTER TABLE operations ADD COLUMN speed_bytes_per_second INTEGER NOT NULL DEFAULT 0"
                )
            }
            if (!hasColumn(database, "operations", "eta_seconds")) {
                database.execSQL(
                    "ALTER TABLE operations ADD COLUMN eta_seconds INTEGER NOT NULL DEFAULT -1"
                )
            }
            if (!hasColumn(database, "sync_runs", "safety_block_details")) {
                database.execSQL(
                    "ALTER TABLE sync_runs ADD COLUMN safety_block_details TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }

    private fun hasColumn(
        database: SQLiteConnection,
        table: String,
        column: String
    ): Boolean {
        database.prepare("PRAGMA table_info($table)").use { statement ->
            while (statement.step()) {
                if (statement.getText(1) == column) return true
            }
        }
        return false
    }

    private fun createSchema(database: SQLiteConnection) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS operations(
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                state TEXT NOT NULL,
                destination_uri TEXT NOT NULL,
                path_schema_version INTEGER NOT NULL,
                queue_position INTEGER NOT NULL,
                created_at_millis INTEGER NOT NULL,
                started_at_millis INTEGER NOT NULL DEFAULT 0,
                updated_at_millis INTEGER NOT NULL,
                completed_at_millis INTEGER NOT NULL DEFAULT 0,
                total_items INTEGER NOT NULL DEFAULT 0,
                completed_items INTEGER NOT NULL DEFAULT 0,
                failed_items INTEGER NOT NULL DEFAULT 0,
                skipped_items INTEGER NOT NULL DEFAULT 0,
                total_bytes INTEGER NOT NULL DEFAULT 0,
                transferred_bytes INTEGER NOT NULL DEFAULT 0,
                current_item TEXT NOT NULL DEFAULT '',
                last_error_category TEXT NOT NULL DEFAULT '',
                last_error_message TEXT NOT NULL DEFAULT '',
                requires_user_action INTEGER NOT NULL DEFAULT 0,
                recovery_reason TEXT NOT NULL DEFAULT '',
                speed_bytes_per_second INTEGER NOT NULL DEFAULT 0,
                eta_seconds INTEGER NOT NULL DEFAULT -1
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS operation_sources(
                operation_id TEXT NOT NULL REFERENCES operations(id) ON DELETE CASCADE,
                ordinal INTEGER NOT NULL,
                source_uri TEXT NOT NULL,
                PRIMARY KEY(operation_id, ordinal)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS operation_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                operation_id TEXT NOT NULL REFERENCES operations(id) ON DELETE CASCADE,
                ordinal INTEGER NOT NULL,
                source_uri TEXT NOT NULL,
                target_uri TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                is_directory INTEGER NOT NULL,
                size_bytes INTEGER NOT NULL,
                modified_millis INTEGER NOT NULL,
                source_fingerprint TEXT NOT NULL,
                state TEXT NOT NULL,
                bytes_completed INTEGER NOT NULL DEFAULT 0,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                temporary_target_uri TEXT NOT NULL DEFAULT '',
                final_result_uri TEXT NOT NULL DEFAULT '',
                error_category TEXT NOT NULL DEFAULT '',
                error_message TEXT NOT NULL DEFAULT '',
                completed_at_millis INTEGER NOT NULL DEFAULT 0,
                UNIQUE(operation_id, ordinal)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_decisions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                operation_id TEXT NOT NULL REFERENCES operations(id) ON DELETE CASCADE,
                item_id INTEGER REFERENCES operation_items(id) ON DELETE CASCADE,
                decision_type TEXT NOT NULL,
                payload TEXT NOT NULL,
                available_responses TEXT NOT NULL,
                selected_response TEXT NOT NULL DEFAULT '',
                apply_to_all INTEGER NOT NULL DEFAULT 0,
                created_at_millis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_profiles(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                source_uri TEXT NOT NULL,
                destination_uri TEXT NOT NULL,
                mode TEXT NOT NULL,
                comparison_policy TEXT NOT NULL,
                conflict_policy TEXT NOT NULL,
                verify_after_copy INTEGER NOT NULL,
                propagate_deletions INTEGER NOT NULL,
                filters_json TEXT NOT NULL,
                protection_json TEXT NOT NULL,
                schedule_json TEXT NOT NULL,
                constraints_json TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                path_schema_version INTEGER NOT NULL,
                baseline_generation INTEGER NOT NULL DEFAULT 0,
                last_run_at_millis INTEGER NOT NULL DEFAULT 0,
                next_run_at_millis INTEGER NOT NULL DEFAULT 0,
                consecutive_failures INTEGER NOT NULL DEFAULT 0,
                created_at_millis INTEGER NOT NULL,
                updated_at_millis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_runs(
                id TEXT PRIMARY KEY,
                profile_id TEXT NOT NULL REFERENCES sync_profiles(id) ON DELETE CASCADE,
                trigger TEXT NOT NULL,
                state TEXT NOT NULL,
                baseline_before INTEGER NOT NULL,
                baseline_after INTEGER NOT NULL DEFAULT 0,
                transfer_operation_id TEXT REFERENCES operations(id) ON DELETE SET NULL,
                safety_block_reason TEXT NOT NULL DEFAULT '',
                safety_block_details TEXT NOT NULL DEFAULT '',
                planned_actions INTEGER NOT NULL DEFAULT 0,
                planned_transfer_bytes INTEGER NOT NULL DEFAULT 0,
                planned_protected_bytes INTEGER NOT NULL DEFAULT 0,
                created_at_millis INTEGER NOT NULL,
                started_at_millis INTEGER NOT NULL DEFAULT 0,
                completed_at_millis INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_snapshot_entries(
                profile_id TEXT NOT NULL REFERENCES sync_profiles(id) ON DELETE CASCADE,
                generation INTEGER NOT NULL,
                side TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                is_directory INTEGER NOT NULL,
                size_bytes INTEGER NOT NULL,
                modified_at_millis INTEGER NOT NULL,
                modified_precision_millis INTEGER NOT NULL,
                revision TEXT NOT NULL DEFAULT '',
                checksum TEXT NOT NULL DEFAULT '',
                provider_identity TEXT NOT NULL,
                PRIMARY KEY(profile_id, generation, side, relative_path)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_actions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL REFERENCES sync_runs(id) ON DELETE CASCADE,
                ordinal INTEGER NOT NULL,
                action_type TEXT NOT NULL,
                direction TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                source_uri TEXT NOT NULL,
                target_uri TEXT NOT NULL,
                source_fingerprint TEXT NOT NULL,
                target_fingerprint TEXT NOT NULL DEFAULT '',
                comparison_reason TEXT NOT NULL,
                state TEXT NOT NULL,
                transfer_item_id INTEGER REFERENCES operation_items(id) ON DELETE SET NULL,
                protected_result_uri TEXT NOT NULL DEFAULT '',
                error_message TEXT NOT NULL DEFAULT '',
                UNIQUE(run_id, ordinal)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS operations_state_queue ON operations(state, queue_position)")
        database.execSQL("CREATE INDEX IF NOT EXISTS items_operation_state ON operation_items(operation_id, state, ordinal)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS items_source_target ON operation_items(operation_id, source_uri, target_uri)")
        database.execSQL("CREATE INDEX IF NOT EXISTS sync_profiles_enabled ON sync_profiles(enabled, next_run_at_millis)")
        database.execSQL("CREATE INDEX IF NOT EXISTS sync_runs_profile_state ON sync_runs(profile_id, state, created_at_millis)")
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS sync_runs_one_active_profile
            ON sync_runs(profile_id)
            WHERE state IN ('PLANNING','PREVIEW_READY','APPROVED','QUEUED','RUNNING',
                'NEEDS_ATTENTION','SAFETY_BLOCKED','PAUSED')
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS sync_actions_run_state ON sync_actions(run_id, state, ordinal)")
        database.execSQL("CREATE INDEX IF NOT EXISTS sync_snapshots_generation ON sync_snapshot_entries(profile_id, generation, side)")
    }

    private fun readSchemaVersion(database: SQLiteConnection): Int {
        database.prepare("PRAGMA user_version").use {
            check(it.step())
            return it.getInt(0)
        }
    }
}
