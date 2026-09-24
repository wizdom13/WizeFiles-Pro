package com.wisso.wizefiles.batchrename

import com.wisso.wizefiles.core.files.model.FileItem
import java.util.UUID

object BatchRenameSessionStore {
    private const val SESSION_TTL_MILLIS = 30 * 60 * 1000L
    private val sessions = mutableMapOf<String, Session>()

    data class Session(
        val files: List<FileItem>,
        val existingNames: List<String>,
        val createdAtMillis: Long = System.currentTimeMillis()
    )

    @Synchronized
    fun create(files: List<FileItem>, existingNames: List<String>): String {
        removeExpiredLocked()
        val id = UUID.randomUUID().toString()
        sessions[id] = Session(files.toList(), existingNames.toList())
        return id
    }

    @Synchronized
    fun get(id: String): Session? {
        removeExpiredLocked()
        return sessions[id]
    }

    @Synchronized
    fun remove(id: String) {
        sessions.remove(id)
    }

    private fun removeExpiredLocked() {
        val cutoff = System.currentTimeMillis() - SESSION_TTL_MILLIS
        sessions.values.removeAll { it.createdAtMillis < cutoff }
    }
}
