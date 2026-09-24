package com.wisso.wizefiles.feature.transfer

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.wisso.wizefiles.core.app.application
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object TransferDatabaseConnection {
    private const val DATABASE_NAME = "transfers.db"
    private val lock = ReentrantLock()
    private var connection: SQLiteConnection? = null

    fun <T> withConnection(block: (SQLiteConnection) -> T): T = lock.withLock {
        block(connection ?: openDatabase().also { connection = it })
    }

    fun clearForTests() = lock.withLock {
        runCatching { connection?.close() }
        connection = null
        databaseFiles().forEach(File::delete)
    }

    fun closeForTests() = lock.withLock {
        runCatching { connection?.close() }
        connection = null
    }

    private fun openDatabase(): SQLiteConnection {
        val database = BundledSQLiteDriver().open(databaseFile().path)
        try {
            database.execSQL("PRAGMA journal_mode=WAL")
            database.execSQL("PRAGMA synchronous=FULL")
            database.execSQL("PRAGMA foreign_keys=ON")
            TransferDatabaseSchema.configure(database)
            return database
        } catch (throwable: Throwable) {
            runCatching { database.close() }
            throw throwable
        }
    }

    private fun databaseFile(): File = application.getDatabasePath(DATABASE_NAME).also {
        it.parentFile?.mkdirs()
    }

    private fun databaseFiles(): List<File> {
        val database = databaseFile()
        return listOf(database, File(database.path + "-wal"), File(database.path + "-shm"))
    }
}
