// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.searchindex

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.util.AppLog
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object SearchIndexDatabase {
    private const val DATABASE_NAME = "search-index.db"
    private const val SCHEMA_VERSION = 1
    private val lock = ReentrantLock()
    private var connection: SQLiteConnection? = null

    fun upsertBatch(records: List<SearchIndexRecord>) {
        if (records.isEmpty()) return
        withConnection { database ->
            database.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                database.prepare(
                    """
                    INSERT INTO files(
                        root_path, path, parent_path, name, name_normalized, is_directory,
                        size_bytes, modified_millis, is_hidden, mime_type, scan_generation
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(path) DO UPDATE SET
                        root_path=excluded.root_path,
                        parent_path=excluded.parent_path,
                        name=excluded.name,
                        name_normalized=excluded.name_normalized,
                        is_directory=excluded.is_directory,
                        size_bytes=excluded.size_bytes,
                        modified_millis=excluded.modified_millis,
                        is_hidden=excluded.is_hidden,
                        mime_type=excluded.mime_type,
                        scan_generation=excluded.scan_generation
                    """.trimIndent()
                ).use { statement ->
                    for (record in records) {
                        statement.bindText(1, record.rootPath)
                        statement.bindText(2, record.path)
                        statement.bindText(3, record.parentPath)
                        statement.bindText(4, record.name)
                        statement.bindText(5, record.normalizedName)
                        statement.bindInt(6, if (record.isDirectory) 1 else 0)
                        statement.bindLong(7, record.sizeBytes)
                        statement.bindLong(8, record.modifiedMillis)
                        statement.bindInt(9, if (record.isHidden) 1 else 0)
                        statement.bindText(10, record.mimeType)
                        statement.bindLong(11, record.generation)
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
    }

    fun beginRootScan(rootPath: String, generation: Long) = withConnection { database ->
        database.prepare(
            """
            INSERT INTO roots(root_path, state, pending_generation, item_count, last_updated_millis)
            VALUES (?, 'INDEXING', ?, 0, 0)
            ON CONFLICT(root_path) DO UPDATE SET state='INDEXING', pending_generation=excluded.pending_generation
            """.trimIndent()
        ).use {
            it.bindText(1, rootPath)
            it.bindLong(2, generation)
            it.step()
        }
    }

    fun updateRootProgress(rootPath: String, itemCount: Long) = withConnection { database ->
        database.prepare("UPDATE roots SET item_count=? WHERE root_path=?").use {
            it.bindLong(1, itemCount)
            it.bindText(2, rootPath)
            it.step()
        }
    }

    fun completeRootScan(rootPath: String, generation: Long, itemCount: Long) =
        withConnection { database ->
            database.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                database.prepare(
                    "DELETE FROM files WHERE root_path=? AND scan_generation<>?"
                ).use {
                    it.bindText(1, rootPath)
                    it.bindLong(2, generation)
                    it.step()
                }
                database.prepare(
                    """
                    UPDATE roots SET state='READY', completed_generation=?, pending_generation=0,
                        item_count=?, last_updated_millis=? WHERE root_path=?
                    """.trimIndent()
                ).use {
                    it.bindLong(1, generation)
                    it.bindLong(2, itemCount)
                    it.bindLong(3, System.currentTimeMillis())
                    it.bindText(4, rootPath)
                    it.step()
                }
                database.execSQL("COMMIT")
            } catch (throwable: Throwable) {
                runCatching { database.execSQL("ROLLBACK") }
                throw throwable
            }
        }

    fun failRootScan(rootPath: String) = withConnection { database ->
        database.prepare(
            "UPDATE roots SET state=CASE WHEN completed_generation>0 THEN 'READY' ELSE 'FAILED' END WHERE root_path=?"
        ).use {
            it.bindText(1, rootPath)
            it.step()
        }
    }

    fun readyRootFor(path: String): String? = withConnection { database ->
        val roots = mutableListOf<String>()
        database.prepare(
            "SELECT root_path FROM roots WHERE state<>'UNAVAILABLE' AND (state='READY' OR completed_generation>0)"
        ).use { statement ->
            while (statement.step()) roots += statement.getText(0)
        }
        roots.filter { path == it || path.startsWith(it.withTrailingSeparator()) }
            .maxByOrNull(String::length)
    }

    fun completedRootFor(path: String): Pair<String, Long>? = withConnection { database ->
        val roots = mutableListOf<Pair<String, Long>>()
        database.prepare(
            "SELECT root_path, completed_generation FROM roots WHERE state='READY' AND completed_generation>0"
        ).use { statement ->
            while (statement.step()) roots += statement.getText(0) to statement.getLong(1)
        }
        roots.filter { (root, _) -> path == root || path.startsWith(root.withTrailingSeparator()) }
            .maxByOrNull { (root, _) -> root.length }
    }

    fun markUnavailableRootsExcept(mountedRootPaths: Set<String>) = withConnection { database ->
        val knownRoots = mutableListOf<String>()
        database.prepare("SELECT root_path FROM roots").use { statement ->
            while (statement.step()) knownRoots += statement.getText(0)
        }
        database.prepare("UPDATE roots SET state='UNAVAILABLE' WHERE root_path=?").use { statement ->
            for (knownRoot in knownRoots) {
                if (knownRoot in mountedRootPaths) continue
                statement.bindText(1, knownRoot)
                statement.step()
                statement.reset()
            }
        }
    }

    fun deleteMissingDirectChildren(
        rootPath: String,
        parentPath: String,
        presentPaths: Set<String>
    ) = withConnection { database ->
        val missingPaths = mutableListOf<String>()
        database.prepare(
            "SELECT path FROM files WHERE root_path=? AND parent_path=?"
        ).use { statement ->
            statement.bindText(1, rootPath)
            statement.bindText(2, parentPath)
            while (statement.step()) {
                statement.getText(0).takeIf { it !in presentPaths }?.let(missingPaths::add)
            }
        }
        if (missingPaths.isEmpty()) return@withConnection
        database.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            database.prepare("DELETE FROM files WHERE path=?").use { statement ->
                for (missingPath in missingPaths) {
                    statement.bindText(1, missingPath)
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

    fun search(
        rootPath: String,
        directoryPath: String,
        query: String,
        includeHidden: Boolean,
        limit: Int,
        offset: Int
    ): List<SearchIndexRecord> = withConnection { database ->
        val normalizedQuery = normalizeSearchText(query.trim())
        if (normalizedQuery.isEmpty()) return@withConnection emptyList()
        val subtreePrefix = directoryPath.withTrailingSeparator()
        val subtreeEnd = subtreeUpperBound(directoryPath)
        val escapedContains = "%${escapeLike(normalizedQuery)}%"
        val escapedPrefix = "${escapeLike(normalizedQuery)}%"
        val useTrigrams = normalizedQuery.codePointCount(0, normalizedQuery.length) >= 3 &&
            normalizedQuery.none { it == '%' || it == '_' || it == '\\' }
        val source = if (useTrigrams) {
            "files JOIN file_names ON file_names.rowid=files.id"
        } else {
            "files"
        }
        val nameClause = if (useTrigrams) {
            "file_names.name_normalized LIKE ?"
        } else {
            "files.name_normalized LIKE ? ESCAPE '\\'"
        }
        val sql =
            """
            SELECT files.root_path, files.path, files.parent_path, files.name,
                   files.is_directory, files.size_bytes, files.modified_millis,
                   files.is_hidden, files.mime_type, files.scan_generation
            FROM $source
            WHERE files.root_path=? AND files.path>=? AND files.path<?
              AND $nameClause
              ${if (includeHidden) "" else "AND files.is_hidden=0"}
            ORDER BY
              CASE
                WHEN files.name_normalized=? THEN 0
                WHEN files.name_normalized LIKE ? ESCAPE '\' THEN 1
                ELSE 2
              END,
              (length(files.path)-length(replace(files.path, '/', ''))) ASC,
              files.modified_millis DESC,
              files.name_normalized ASC
            LIMIT ? OFFSET ?
            """.trimIndent()
        buildList {
            database.prepare(sql).use { statement ->
                statement.bindText(1, rootPath)
                statement.bindText(2, subtreePrefix)
                statement.bindText(3, subtreeEnd)
                statement.bindText(4, if (useTrigrams) "%$normalizedQuery%" else escapedContains)
                statement.bindText(5, normalizedQuery)
                statement.bindText(6, escapedPrefix)
                statement.bindInt(7, limit.coerceIn(1, 1_000))
                statement.bindInt(8, offset.coerceAtLeast(0))
                while (statement.step()) {
                    add(
                        SearchIndexRecord(
                            rootPath = statement.getText(0),
                            path = statement.getText(1),
                            parentPath = statement.getText(2),
                            name = statement.getText(3),
                            isDirectory = statement.getInt(4) != 0,
                            sizeBytes = statement.getLong(5),
                            modifiedMillis = statement.getLong(6),
                            isHidden = statement.getInt(7) != 0,
                            mimeType = statement.getText(8),
                            generation = statement.getLong(9)
                        )
                    )
                }
            }
        }
    }

    fun status(): SearchIndexStatus = withConnection { database ->
        var hasRows = false
        var indexing = false
        var failed = false
        var count = 0L
        var lastUpdated = 0L
        val roots = mutableListOf<String>()
        database.prepare(
            "SELECT root_path, state, item_count, last_updated_millis FROM roots ORDER BY root_path"
        ).use { statement ->
            while (statement.step()) {
                val state = statement.getText(1)
                if (state == "UNAVAILABLE") continue
                hasRows = true
                roots += statement.getText(0)
                when (state) {
                    "INDEXING" -> indexing = true
                    "FAILED" -> failed = true
                }
                count += statement.getLong(2)
                lastUpdated = maxOf(lastUpdated, statement.getLong(3))
            }
        }
        SearchIndexStatus(
            state = when {
                indexing -> SearchIndexStatus.State.INDEXING
                failed -> SearchIndexStatus.State.FAILED
                hasRows && lastUpdated > 0L -> SearchIndexStatus.State.READY
                else -> SearchIndexStatus.State.EMPTY
            },
            itemCount = count,
            lastUpdatedMillis = lastUpdated,
            indexedRoots = roots
        )
    }

    fun clear() = lock.withLock {
        resetDatabaseFilesLocked()
    }

    fun sizeBytes(): Long = sequenceOf(
        databaseFile(),
        File(databaseFile().path + "-wal"),
        File(databaseFile().path + "-shm")
    ).filter(File::exists).sumOf(File::length)

    private fun <T> withConnection(block: (SQLiteConnection) -> T): T = lock.withLock {
        try {
            block(connection ?: openDatabase().also { connection = it })
        } catch (exception: SQLiteException) {
            if (!exception.indicatesCorruption()) {
                AppLog.e("SearchIndex", "Database operation failed", exception)
                throw exception
            }
            AppLog.e("SearchIndex", "Database is unavailable; rebuilding it", exception)
            resetDatabaseFilesLocked()
            val rebuilt = openDatabase().also { connection = it }
            block(rebuilt)
        } catch (throwable: Throwable) {
            AppLog.e("SearchIndex", "Database operation failed", throwable)
            throw throwable
        }
    }

    private fun SQLiteException.indicatesCorruption(): Boolean {
        val normalizedMessage = message.orEmpty().lowercase()
        return "malformed" in normalizedMessage ||
            "not a database" in normalizedMessage ||
            "database corruption" in normalizedMessage ||
            "file is encrypted" in normalizedMessage
    }

    private fun resetDatabaseFilesLocked() {
        runCatching { connection?.close() }
        connection = null
        databaseFile().delete()
        File(databaseFile().path + "-wal").delete()
        File(databaseFile().path + "-shm").delete()
    }

    private fun openDatabase(): SQLiteConnection {
        val database = BundledSQLiteDriver().open(databaseFile().path)
        database.execSQL("PRAGMA journal_mode=WAL")
        database.execSQL("PRAGMA synchronous=NORMAL")
        database.execSQL("PRAGMA temp_store=MEMORY")
        database.execSQL("PRAGMA foreign_keys=ON")
        createSchema(database)
        return database
    }

    private fun createSchema(database: SQLiteConnection) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS files(
                id INTEGER PRIMARY KEY,
                root_path TEXT NOT NULL,
                path TEXT NOT NULL UNIQUE,
                parent_path TEXT NOT NULL,
                name TEXT NOT NULL,
                name_normalized TEXT NOT NULL,
                is_directory INTEGER NOT NULL,
                size_bytes INTEGER NOT NULL,
                modified_millis INTEGER NOT NULL,
                is_hidden INTEGER NOT NULL,
                mime_type TEXT NOT NULL,
                scan_generation INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS roots(
                root_path TEXT PRIMARY KEY,
                state TEXT NOT NULL,
                completed_generation INTEGER NOT NULL DEFAULT 0,
                pending_generation INTEGER NOT NULL DEFAULT 0,
                item_count INTEGER NOT NULL DEFAULT 0,
                last_updated_millis INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS files_scope ON files(root_path, path)")
        database.execSQL("CREATE INDEX IF NOT EXISTS files_parent ON files(root_path, parent_path)")
        database.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS file_names USING fts5(
                name_normalized,
                content='files',
                content_rowid='id',
                tokenize='trigram case_sensitive 0',
                detail='full'
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE TRIGGER IF NOT EXISTS files_ai AFTER INSERT ON files BEGIN " +
                "INSERT INTO file_names(rowid, name_normalized) VALUES (new.id, new.name_normalized); END"
        )
        database.execSQL(
            "CREATE TRIGGER IF NOT EXISTS files_ad AFTER DELETE ON files BEGIN " +
                "INSERT INTO file_names(file_names, rowid, name_normalized) " +
                "VALUES ('delete', old.id, old.name_normalized); END"
        )
        database.execSQL(
            "CREATE TRIGGER IF NOT EXISTS files_au AFTER UPDATE OF name_normalized ON files " +
                "WHEN old.name_normalized<>new.name_normalized BEGIN " +
                "INSERT INTO file_names(file_names, rowid, name_normalized) " +
                "VALUES ('delete', old.id, old.name_normalized); " +
                "INSERT INTO file_names(rowid, name_normalized) VALUES (new.id, new.name_normalized); END"
        )
        database.execSQL("PRAGMA user_version=$SCHEMA_VERSION")
    }

    private fun databaseFile(): File = application.getDatabasePath(DATABASE_NAME).also {
        it.parentFile?.mkdirs()
    }
}

internal fun String.withTrailingSeparator(): String = if (endsWith('/')) this else "$this/"

internal fun subtreeUpperBound(directoryPath: String): String = directoryPath.trimEnd('/') + "0"

internal fun escapeLike(value: String): String = buildString(value.length) {
    for (character in value) {
        if (character == '\\' || character == '%' || character == '_') append('\\')
        append(character)
    }
}
