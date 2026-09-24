// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import com.wisso.wizefiles.feature.sync.SyncComparisonPolicy
import com.wisso.wizefiles.feature.sync.SyncConflictPolicy
import com.wisso.wizefiles.feature.sync.SyncMode
import com.wisso.wizefiles.feature.sync.SyncProfile

internal object SyncProfileDao {

    fun upsertSyncProfile(profile: SyncProfile) = withConnection { database ->
        database.prepare(
            """
            INSERT INTO sync_profiles(
                id, name, source_uri, destination_uri, mode, comparison_policy,
                conflict_policy, verify_after_copy, propagate_deletions, filters_json,
                protection_json, schedule_json, constraints_json, enabled,
                path_schema_version, baseline_generation, last_run_at_millis,
                next_run_at_millis, consecutive_failures, created_at_millis, updated_at_millis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name, source_uri=excluded.source_uri,
                destination_uri=excluded.destination_uri, mode=excluded.mode,
                comparison_policy=excluded.comparison_policy,
                conflict_policy=excluded.conflict_policy,
                verify_after_copy=excluded.verify_after_copy,
                propagate_deletions=excluded.propagate_deletions,
                filters_json=excluded.filters_json, protection_json=excluded.protection_json,
                schedule_json=excluded.schedule_json, constraints_json=excluded.constraints_json,
                enabled=excluded.enabled, path_schema_version=excluded.path_schema_version,
                baseline_generation=excluded.baseline_generation,
                last_run_at_millis=excluded.last_run_at_millis,
                next_run_at_millis=excluded.next_run_at_millis,
                consecutive_failures=excluded.consecutive_failures,
                updated_at_millis=excluded.updated_at_millis
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, profile.id)
            statement.bindText(2, profile.name)
            statement.bindText(3, profile.sourceUri)
            statement.bindText(4, profile.destinationUri)
            statement.bindText(5, profile.mode.name)
            statement.bindText(6, profile.comparisonPolicy.name)
            statement.bindText(7, profile.conflictPolicy.name)
            statement.bindInt(8, if (profile.verifyAfterCopy) 1 else 0)
            statement.bindInt(9, if (profile.propagateDeletions) 1 else 0)
            statement.bindText(10, profile.filtersJson)
            statement.bindText(11, profile.protectionJson)
            statement.bindText(12, profile.scheduleJson)
            statement.bindText(13, profile.constraintsJson)
            statement.bindInt(14, if (profile.enabled) 1 else 0)
            statement.bindInt(15, profile.pathSchemaVersion)
            statement.bindLong(16, profile.baselineGeneration)
            statement.bindLong(17, profile.lastRunAtMillis)
            statement.bindLong(18, profile.nextRunAtMillis)
            statement.bindInt(19, profile.consecutiveFailures)
            statement.bindLong(20, profile.createdAtMillis)
            statement.bindLong(21, profile.updatedAtMillis)
            statement.step()
        }
    }

    fun syncProfile(profileId: String): SyncProfile? = withConnection { database ->
        database.prepare("SELECT ${syncProfileColumns()} FROM sync_profiles WHERE id=?").use {
            it.bindText(1, profileId)
            if (it.step()) it.toSyncProfile() else null
        }
    }

    fun syncProfiles(): List<SyncProfile> = withConnection { database ->
        buildList {
            database.prepare(
                "SELECT ${syncProfileColumns()} FROM sync_profiles ORDER BY name COLLATE NOCASE"
            ).use { statement ->
                while (statement.step()) add(statement.toSyncProfile())
            }
        }
    }

    fun deleteSyncProfile(profileId: String): Boolean = withConnection { database ->
        database.prepare(
            """
            DELETE FROM sync_profiles WHERE id=? AND NOT EXISTS(
                SELECT 1 FROM sync_runs WHERE profile_id=? AND
                state IN ('PLANNING','APPROVED','QUEUED','RUNNING',
                    'NEEDS_ATTENTION','PAUSED')
            ) RETURNING id
            """.trimIndent()
        ).use {
            it.bindText(1, profileId)
            it.bindText(2, profileId)
            it.step()
        }
    }

    private fun syncProfile(database: SQLiteConnection, profileId: String): SyncProfile? {
        database.prepare("SELECT ${syncProfileColumns()} FROM sync_profiles WHERE id=?").use {
            it.bindText(1, profileId)
            return if (it.step()) it.toSyncProfile() else null
        }
    }

    private fun syncProfileColumns(): String =
        "id, name, source_uri, destination_uri, mode, comparison_policy, conflict_policy, " +
            "verify_after_copy, propagate_deletions, filters_json, protection_json, " +
            "schedule_json, constraints_json, enabled, path_schema_version, " +
            "baseline_generation, last_run_at_millis, next_run_at_millis, " +
            "consecutive_failures, created_at_millis, updated_at_millis"

    private fun androidx.sqlite.SQLiteStatement.toSyncProfile() = SyncProfile(
        id = getText(0),
        name = getText(1),
        sourceUri = getText(2),
        destinationUri = getText(3),
        mode = SyncMode.valueOf(getText(4)),
        comparisonPolicy = SyncComparisonPolicy.valueOf(getText(5)),
        conflictPolicy = SyncConflictPolicy.valueOf(getText(6)),
        verifyAfterCopy = getInt(7) != 0,
        propagateDeletions = getInt(8) != 0,
        filtersJson = getText(9),
        protectionJson = getText(10),
        scheduleJson = getText(11),
        constraintsJson = getText(12),
        enabled = getInt(13) != 0,
        pathSchemaVersion = getInt(14),
        baselineGeneration = getLong(15),
        lastRunAtMillis = getLong(16),
        nextRunAtMillis = getLong(17),
        consecutiveFailures = getInt(18),
        createdAtMillis = getLong(19),
        updatedAtMillis = getLong(20)
    )

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        TransferDatabaseConnection.withConnection(block)
}
