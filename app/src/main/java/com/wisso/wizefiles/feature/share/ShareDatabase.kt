package com.wisso.wizefiles.feature.share

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.wisso.wizefiles.core.app.application
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Sharing tables intentionally live in transfers.db but own an independent schema marker. */
internal object ShareDatabase {
    private val lock = ReentrantLock()
    private var connection: SQLiteConnection? = null

    fun saveProfile(profile: ShareProfile, roots: List<ShareRoot>) = withConnection { db ->
        db.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            db.prepare("""
                INSERT OR REPLACE INTO share_profiles(
                    id,name,protocols,browser_port,ftp_port,passive_port_start,passive_port_end,
                    permission,approve_destructive,ftp_writable,inactivity_minutes,certificate_alias,
                    path_schema_version,created_at_millis,updated_at_millis
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent()).use { s ->
                s.bindText(1, profile.id); s.bindText(2, profile.name)
                s.bindText(3, profile.protocols.joinToString(",") { it.name })
                s.bindInt(4, profile.browserPort); s.bindInt(5, profile.ftpPort)
                s.bindInt(6, profile.passivePortStart); s.bindInt(7, profile.passivePortEnd)
                s.bindText(8, profile.permission.name); s.bindInt(9, profile.approveDestructiveInBrowser.asInt())
                s.bindInt(10, profile.ftpWritable.asInt()); s.bindInt(11, profile.inactivityMinutes)
                s.bindText(12, profile.certificateAlias); s.bindInt(13, profile.pathSchemaVersion)
                s.bindLong(14, profile.createdAtMillis); s.bindLong(15, profile.updatedAtMillis); s.step()
            }
            db.prepare("DELETE FROM share_roots WHERE profile_id=?").use { it.bindText(1, profile.id); it.step() }
            db.prepare("""
                INSERT INTO share_roots(id,profile_id,alias,app_path_uri,provider_identity,
                    readable,creatable,updatable,deletable,path_schema_version)
                VALUES(?,?,?,?,?,?,?,?,?,?)
            """.trimIndent()).use { s ->
                roots.forEach { root ->
                    require(root.profileId == profile.id)
                    s.bindText(1, root.id); s.bindText(2, root.profileId); s.bindText(3, root.alias)
                    s.bindText(4, root.appPathUri); s.bindText(5, root.providerIdentity)
                    s.bindInt(6, root.readable.asInt()); s.bindInt(7, root.creatable.asInt())
                    s.bindInt(8, root.updatable.asInt()); s.bindInt(9, root.deletable.asInt())
                    s.bindInt(10, root.pathSchemaVersion); s.step(); s.reset()
                }
            }
            db.execSQL("COMMIT")
        } catch (t: Throwable) { runCatching { db.execSQL("ROLLBACK") }; throw t }
    }

    fun profiles(): List<ShareProfile> = withConnection { db ->
        buildList {
            db.prepare("SELECT id,name,protocols,browser_port,ftp_port,passive_port_start,passive_port_end,permission,approve_destructive,ftp_writable,inactivity_minutes,certificate_alias,path_schema_version,created_at_millis,updated_at_millis FROM share_profiles ORDER BY name COLLATE NOCASE").use { s ->
                while (s.step()) add(ShareProfile(
                    id=s.getText(0), name=s.getText(1), protocols=s.getText(2).split(',').filter(String::isNotBlank).map(ShareProtocol::valueOf).toSet(),
                    browserPort=s.getInt(3), ftpPort=s.getInt(4), passivePortStart=s.getInt(5), passivePortEnd=s.getInt(6),
                    permission=SharePermission.valueOf(s.getText(7)), approveDestructiveInBrowser=s.getInt(8)!=0,
                    ftpWritable=s.getInt(9)!=0, inactivityMinutes=s.getInt(10), certificateAlias=s.getText(11),
                    pathSchemaVersion=s.getInt(12), createdAtMillis=s.getLong(13), updatedAtMillis=s.getLong(14)))
            }
        }
    }

    fun profile(id: String): ShareProfile? = profiles().firstOrNull { it.id == id }

    fun roots(profileId: String): List<ShareRoot> = withConnection { db -> buildList {
        db.prepare("SELECT id,profile_id,alias,app_path_uri,provider_identity,readable,creatable,updatable,deletable,path_schema_version FROM share_roots WHERE profile_id=? ORDER BY rowid").use { s ->
            s.bindText(1, profileId)
            while (s.step()) add(ShareRoot(s.getText(0),s.getText(1),s.getText(2),s.getText(3),s.getText(4),s.getInt(5)!=0,s.getInt(6)!=0,s.getInt(7)!=0,s.getInt(8)!=0,s.getInt(9)))
        }
    } }

    fun startSession(session: ShareSession) = withConnection { db ->
        db.prepare("INSERT INTO share_sessions(id,profile_id,network_identity,state,started_at_millis) VALUES(?,?,?,?,?)").use { s ->
            s.bindText(1,session.id); s.bindText(2,session.profileId); s.bindText(3,session.networkIdentity)
            s.bindText(4,session.state.name); s.bindLong(5,session.startedAtMillis); s.step()
        }
    }

    fun markInterruptedSessions(nowMillis:Long=System.currentTimeMillis())=withConnection { db ->db.prepare("UPDATE share_sessions SET state='STOPPED',ended_at_millis=?,stop_reason='PROCESS_TERMINATED' WHERE state IN ('STARTING','ACTIVE')").use{it.bindLong(1,nowMillis);it.step()} }

    fun updateSession(session: ShareSession) = withConnection { db ->
        db.prepare("UPDATE share_sessions SET state=?,ended_at_millis=?,stop_reason=?,uploaded_bytes=?,downloaded_bytes=?,client_count=? WHERE id=?").use { s ->
            s.bindText(1,session.state.name); s.bindLong(2,session.endedAtMillis); s.bindText(3,session.stopReason?.name.orEmpty())
            s.bindLong(4,session.uploadedBytes); s.bindLong(5,session.downloadedBytes); s.bindInt(6,session.clientCount); s.bindText(7,session.id); s.step()
        }
    }

    fun audit(event: ShareAuditEvent) = withConnection { db ->
        db.prepare("INSERT INTO share_audit_events(session_id,client_id,client_address,operation,friendly_path,result,error_category,transfer_operation_id,created_at_millis) VALUES(?,?,?,?,?,?,?,?,?)").use { s ->
            s.bindText(1,event.sessionId); s.bindText(2,event.clientId); s.bindText(3,event.clientAddress)
            s.bindText(4,event.operation); s.bindText(5,event.friendlyPath); s.bindText(6,event.result)
            s.bindText(7,event.errorCategory); s.bindText(8,event.transferOperationId); s.bindLong(9,event.createdAtMillis); s.step()
        }
    }

    fun auditEvents(sessionId:String,limit:Int=200):List<ShareAuditEvent> = withConnection { db ->buildList { db.prepare("SELECT id,session_id,client_id,client_address,operation,friendly_path,result,error_category,transfer_operation_id,created_at_millis FROM share_audit_events WHERE session_id=? ORDER BY created_at_millis DESC LIMIT ?").use { s ->s.bindText(1,sessionId);s.bindInt(2,limit.coerceIn(1,1000));while(s.step())add(ShareAuditEvent(s.getLong(0),s.getText(1),s.getText(2),s.getText(3),s.getText(4),s.getText(5),s.getText(6),s.getText(7),s.getText(8),s.getLong(9))) } } }

    fun pruneAudit(olderThanMillis: Long): Int = withConnection { db ->
        var count=0
        db.prepare("DELETE FROM share_audit_events WHERE created_at_millis<? RETURNING id").use { s ->
            s.bindLong(1,olderThanMillis); while(s.step()) count++
        }; count
    }

    fun saveUpload(upload:ShareUploadCheckpoint)=withConnection { db ->
        db.prepare("INSERT OR REPLACE INTO share_uploads(id,session_id,root_id,relative_path,temporary_uri,expected_bytes,completed_bytes,transfer_operation_id,updated_at_millis) VALUES(?,?,?,?,?,?,?,?,?)").use { s ->
            s.bindText(1,upload.id);s.bindText(2,upload.sessionId);s.bindText(3,upload.rootId);s.bindText(4,upload.relativePath);s.bindText(5,upload.temporaryUri);s.bindLong(6,upload.expectedBytes);s.bindLong(7,upload.completedBytes);s.bindText(8,upload.transferOperationId);s.bindLong(9,upload.updatedAtMillis);s.step()
        }
    }

    fun upload(id:String):ShareUploadCheckpoint?=withConnection { db ->
        db.prepare("SELECT id,session_id,root_id,relative_path,temporary_uri,expected_bytes,completed_bytes,transfer_operation_id,updated_at_millis FROM share_uploads WHERE id=?").use { s ->s.bindText(1,id);if(!s.step())null else ShareUploadCheckpoint(s.getText(0),s.getText(1),s.getText(2),s.getText(3),s.getText(4),s.getLong(5),s.getLong(6),s.getText(7),s.getLong(8)) }
    }

    fun deleteUpload(id:String)=withConnection { db ->db.prepare("DELETE FROM share_uploads WHERE id=?").use{it.bindText(1,id);it.step()} }

    fun savePendingAction(action:PendingShareAction)=withConnection { db ->
        db.prepare("INSERT OR REPLACE INTO share_pending_actions(id,session_id,client_id,action_type,source_root_id,source_relative_path,target_root_id,target_relative_path,expected_revision,upload_id,state,created_at_millis) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)").use { s ->
            s.bindText(1,action.id);s.bindText(2,action.sessionId);s.bindText(3,action.clientId);s.bindText(4,action.type.name);s.bindText(5,action.sourceRootId);s.bindText(6,action.sourceRelativePath);s.bindText(7,action.targetRootId);s.bindText(8,action.targetRelativePath);s.bindText(9,action.expectedRevision);s.bindText(10,action.uploadId);s.bindText(11,action.state.name);s.bindLong(12,action.createdAtMillis);s.step()
        }
    }

    fun pendingActions(sessionId:String):List<PendingShareAction> = withConnection { db ->buildList { db.prepare("SELECT id,session_id,client_id,action_type,source_root_id,source_relative_path,target_root_id,target_relative_path,expected_revision,upload_id,state,created_at_millis FROM share_pending_actions WHERE session_id=? AND state='WAITING' ORDER BY created_at_millis").use { s ->s.bindText(1,sessionId);while(s.step())add(PendingShareAction(id=s.getText(0),sessionId=s.getText(1),clientId=s.getText(2),type=PendingShareActionType.valueOf(s.getText(3)),sourceRootId=s.getText(4),sourceRelativePath=s.getText(5),targetRootId=s.getText(6),targetRelativePath=s.getText(7),expectedRevision=s.getText(8),uploadId=s.getText(9),state=PendingShareActionState.valueOf(s.getText(10)),createdAtMillis=s.getLong(11))) } } }

    fun setPendingActionState(id:String,state:PendingShareActionState)=withConnection { db ->db.prepare("UPDATE share_pending_actions SET state=? WHERE id=?").use{it.bindText(1,state.name);it.bindText(2,id);it.step()} }

    fun clearForTests() = lock.withLock { connection?.close(); connection=null; databaseFiles().forEach(File::delete) }

    private fun <T> withConnection(block:(SQLiteConnection)->T):T = lock.withLock {
        val db=connection ?: BundledSQLiteDriver().open(databaseFile().absolutePath).also { opened ->
            opened.execSQL("PRAGMA foreign_keys=ON"); opened.execSQL("PRAGMA journal_mode=WAL"); createSchema(opened); connection=opened
        }; block(db)
    }

    private fun createSchema(db:SQLiteConnection) {
        db.execSQL("CREATE TABLE IF NOT EXISTS share_schema(version INTEGER NOT NULL)")
        db.execSQL("INSERT INTO share_schema(version) SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM share_schema)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS share_profiles(
            id TEXT PRIMARY KEY,name TEXT NOT NULL,protocols TEXT NOT NULL,browser_port INTEGER NOT NULL,ftp_port INTEGER NOT NULL,
            passive_port_start INTEGER NOT NULL,passive_port_end INTEGER NOT NULL,permission TEXT NOT NULL,approve_destructive INTEGER NOT NULL,
            ftp_writable INTEGER NOT NULL,inactivity_minutes INTEGER NOT NULL,certificate_alias TEXT NOT NULL,path_schema_version INTEGER NOT NULL,
            created_at_millis INTEGER NOT NULL,updated_at_millis INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS share_roots(
            id TEXT PRIMARY KEY,profile_id TEXT NOT NULL REFERENCES share_profiles(id) ON DELETE CASCADE,alias TEXT NOT NULL,
            app_path_uri TEXT NOT NULL,provider_identity TEXT NOT NULL,readable INTEGER NOT NULL,creatable INTEGER NOT NULL,
            updatable INTEGER NOT NULL,deletable INTEGER NOT NULL,path_schema_version INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS share_sessions(
            id TEXT PRIMARY KEY,profile_id TEXT NOT NULL,network_identity TEXT NOT NULL,state TEXT NOT NULL,started_at_millis INTEGER NOT NULL,
            ended_at_millis INTEGER NOT NULL DEFAULT 0,stop_reason TEXT NOT NULL DEFAULT '',uploaded_bytes INTEGER NOT NULL DEFAULT 0,
            downloaded_bytes INTEGER NOT NULL DEFAULT 0,client_count INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS share_audit_events(
            id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT NOT NULL,client_id TEXT NOT NULL,client_address TEXT NOT NULL,
            operation TEXT NOT NULL,friendly_path TEXT NOT NULL,result TEXT NOT NULL,error_category TEXT NOT NULL DEFAULT '',
            transfer_operation_id TEXT NOT NULL DEFAULT '',created_at_millis INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS share_roots_profile ON share_roots(profile_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS share_audit_session_time ON share_audit_events(session_id,created_at_millis)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS share_uploads(
            id TEXT PRIMARY KEY,session_id TEXT NOT NULL,root_id TEXT NOT NULL,relative_path TEXT NOT NULL,temporary_uri TEXT NOT NULL,
            expected_bytes INTEGER NOT NULL,completed_bytes INTEGER NOT NULL,transfer_operation_id TEXT NOT NULL,updated_at_millis INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS share_pending_actions(
            id TEXT PRIMARY KEY,session_id TEXT NOT NULL,client_id TEXT NOT NULL,action_type TEXT NOT NULL,source_root_id TEXT NOT NULL,
            source_relative_path TEXT NOT NULL,target_root_id TEXT NOT NULL,target_relative_path TEXT NOT NULL,expected_revision TEXT NOT NULL,
            upload_id TEXT NOT NULL DEFAULT '',state TEXT NOT NULL,created_at_millis INTEGER NOT NULL)""")
        migratePendingActionsSchema(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS share_pending_session_state ON share_pending_actions(session_id,state,created_at_millis)")
    }

    internal fun migratePendingActionsSchema(db: SQLiteConnection) {
        db.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val version = db.prepare("SELECT version FROM share_schema LIMIT 1").use { statement ->
                check(statement.step()) { "Share schema version row is missing" }
                statement.getInt(0)
            }
            check(version <= SHARE_SCHEMA_VERSION) {
                "Share database was created by a newer app version"
            }
            if ("upload_id" !in pendingActionColumns(db)) {
                db.execSQL(
                    "ALTER TABLE share_pending_actions " +
                        "ADD COLUMN upload_id TEXT NOT NULL DEFAULT ''"
                )
            }
            check("upload_id" in pendingActionColumns(db)) {
                "Share pending-action migration did not create upload_id"
            }
            db.execSQL(
                "UPDATE share_schema SET version=$SHARE_SCHEMA_VERSION " +
                    "WHERE version<$SHARE_SCHEMA_VERSION"
            )
            db.execSQL("COMMIT")
        } catch (throwable: Throwable) {
            runCatching { db.execSQL("ROLLBACK") }
            throw throwable
        }
    }

    private fun pendingActionColumns(db: SQLiteConnection): Set<String> = buildSet {
        db.prepare("PRAGMA table_info(share_pending_actions)").use { statement ->
            while (statement.step()) add(statement.getText(1))
        }
    }

    private const val SHARE_SCHEMA_VERSION = 2

    private fun Boolean.asInt()=if(this)1 else 0
    private fun databaseFile()=application.getDatabasePath("transfers.db").also { it.parentFile?.mkdirs() }
    private fun databaseFiles()=listOf(databaseFile(),File(databaseFile().path+"-wal"),File(databaseFile().path+"-shm"))
}
