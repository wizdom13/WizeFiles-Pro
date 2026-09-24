package com.wisso.wizefiles.feature.share

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShareDatabaseMigrationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `version one pending actions migrate atomically to upload id`() {
        val file = temporary.newFile("share-migration.db")
        BundledSQLiteDriver().open(file.absolutePath).use { database ->
            database.execSQL("CREATE TABLE share_schema(version INTEGER NOT NULL)")
            database.execSQL("INSERT INTO share_schema(version) VALUES(1)")
            database.execSQL(
                """CREATE TABLE share_pending_actions(
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    client_id TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    source_root_id TEXT NOT NULL,
                    source_relative_path TEXT NOT NULL,
                    target_root_id TEXT NOT NULL,
                    target_relative_path TEXT NOT NULL,
                    expected_revision TEXT NOT NULL,
                    state TEXT NOT NULL,
                    created_at_millis INTEGER NOT NULL
                )""".trimIndent()
            )

            ShareDatabase.migratePendingActionsSchema(database)

            val columns = buildSet {
                database.prepare("PRAGMA table_info(share_pending_actions)").use { statement ->
                    while (statement.step()) add(statement.getText(1))
                }
            }
            val version = database.prepare("SELECT version FROM share_schema").use { statement ->
                assertTrue(statement.step())
                statement.getInt(0)
            }
            assertTrue("upload_id" in columns)
            assertEquals(2, version)
        }
    }

    @Test
    fun `newer share schema versions are rejected without being overwritten`() {
        val file = temporary.newFile("share-downgrade.db")
        BundledSQLiteDriver().open(file.absolutePath).use { database ->
            database.execSQL("CREATE TABLE share_schema(version INTEGER NOT NULL)")
            database.execSQL("INSERT INTO share_schema(version) VALUES(99)")
            database.execSQL(
                "CREATE TABLE share_pending_actions(" +
                    "id TEXT PRIMARY KEY, upload_id TEXT NOT NULL DEFAULT '')"
            )

            assertTrue(
                runCatching { ShareDatabase.migratePendingActionsSchema(database) }.isFailure
            )
            val version = database.prepare("SELECT version FROM share_schema").use { statement ->
                assertTrue(statement.step())
                statement.getInt(0)
            }
            assertEquals(99, version)
        }
    }
}
