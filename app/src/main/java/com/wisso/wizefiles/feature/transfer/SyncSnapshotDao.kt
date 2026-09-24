package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.wisso.wizefiles.feature.sync.SyncRun
import com.wisso.wizefiles.feature.sync.SyncSide
import com.wisso.wizefiles.feature.sync.SyncSnapshotEntry

internal object SyncSnapshotDao {

    fun insertSyncSnapshotEntries(entries: List<SyncSnapshotEntry>) = withConnection { database ->
        if (entries.isEmpty()) return@withConnection
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            database.prepare(
                """
                INSERT OR REPLACE INTO sync_snapshot_entries(
                    profile_id, generation, side, relative_path, is_directory,
                    size_bytes, modified_at_millis, modified_precision_millis,
                    revision, checksum, provider_identity
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                entries.forEach { entry ->
                    statement.bindText(1, entry.profileId)
                    statement.bindLong(2, entry.generation)
                    statement.bindText(3, entry.side.name)
                    statement.bindText(4, entry.relativePath)
                    statement.bindInt(5, if (entry.isDirectory) 1 else 0)
                    statement.bindLong(6, entry.sizeBytes)
                    statement.bindLong(7, entry.modifiedAtMillis)
                    statement.bindLong(8, entry.modifiedPrecisionMillis)
                    statement.bindText(9, entry.revision)
                    statement.bindText(10, entry.checksum)
                    statement.bindText(11, entry.providerIdentity)
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

    fun syncSnapshotEntries(
        profileId: String,
        generation: Long,
        side: SyncSide
    ): List<SyncSnapshotEntry> = withConnection { database ->
        buildList {
            database.prepare(
                """
                SELECT relative_path, is_directory, size_bytes, modified_at_millis,
                    modified_precision_millis, revision, checksum, provider_identity
                FROM sync_snapshot_entries
                WHERE profile_id=? AND generation=? AND side=? ORDER BY relative_path
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, profileId)
                statement.bindLong(2, generation)
                statement.bindText(3, side.name)
                while (statement.step()) {
                    add(
                        SyncSnapshotEntry(
                            profileId = profileId,
                            generation = generation,
                            side = side,
                            relativePath = statement.getText(0),
                            isDirectory = statement.getInt(1) != 0,
                            sizeBytes = statement.getLong(2),
                            modifiedAtMillis = statement.getLong(3),
                            modifiedPrecisionMillis = statement.getLong(4),
                            revision = statement.getText(5),
                            checksum = statement.getText(6),
                            providerIdentity = statement.getText(7)
                        )
                    )
                }
            }
        }
    }

    fun commitSyncBaseline(
        runId: String,
        sourceEntries: List<SyncSnapshotEntry>,
        destinationEntries: List<SyncSnapshotEntry>,
        nowMillis: Long = System.currentTimeMillis()
    ): SyncRun = withConnection { database ->
        val run = requireNotNull(SyncRunDao.syncRun(runId))
        val profile = requireNotNull(SyncProfileDao.syncProfile(run.profileId))
        val generation = profile.baselineGeneration + 1
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            insertSnapshotEntries(database, sourceEntries.map { it.copy(generation = generation) })
            insertSnapshotEntries(database, destinationEntries.map { it.copy(generation = generation) })
            database.prepare(
                """
                UPDATE sync_profiles SET baseline_generation=?, last_run_at_millis=?,
                    consecutive_failures=0, updated_at_millis=? WHERE id=?
                """.trimIndent()
            ).use { statement ->
                statement.bindLong(1, generation)
                statement.bindLong(2, nowMillis)
                statement.bindLong(3, nowMillis)
                statement.bindText(4, profile.id)
                statement.step()
            }
            database.prepare(
                """
                UPDATE sync_runs SET state='COMPLETED', baseline_after=?,
                    completed_at_millis=? WHERE id=?
                """.trimIndent()
            ).use { statement ->
                statement.bindLong(1, generation)
                statement.bindLong(2, nowMillis)
                statement.bindText(3, runId)
                statement.step()
            }
            database.execSQL("COMMIT")
            requireNotNull(SyncRunDao.syncRun(runId))
        } catch (throwable: Throwable) {
            runCatching { database.execSQL("ROLLBACK") }
            throw throwable
        }
    }

    private fun insertSnapshotEntries(
        database: SQLiteConnection,
        entries: List<SyncSnapshotEntry>
    ) {
        if (entries.isEmpty()) return
        database.prepare(
            """
            INSERT OR REPLACE INTO sync_snapshot_entries(
                profile_id, generation, side, relative_path, is_directory,
                size_bytes, modified_at_millis, modified_precision_millis,
                revision, checksum, provider_identity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            entries.forEach { entry ->
                statement.bindText(1, entry.profileId)
                statement.bindLong(2, entry.generation)
                statement.bindText(3, entry.side.name)
                statement.bindText(4, entry.relativePath)
                statement.bindInt(5, if (entry.isDirectory) 1 else 0)
                statement.bindLong(6, entry.sizeBytes)
                statement.bindLong(7, entry.modifiedAtMillis)
                statement.bindLong(8, entry.modifiedPrecisionMillis)
                statement.bindText(9, entry.revision)
                statement.bindText(10, entry.checksum)
                statement.bindText(11, entry.providerIdentity)
                statement.step()
                statement.reset()
            }
        }
    }

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        TransferDatabaseConnection.withConnection(block)
}
