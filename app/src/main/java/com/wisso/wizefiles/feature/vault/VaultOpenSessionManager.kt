// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.util.createViewIntent
import java.util.UUID

internal data class VaultOpenSession(
    val sessionId: String,
    val vaultId: String,
    val entryId: String,
    val displayName: String,
    val mimeType: MimeType,
    val originalSize: Long,
    val originalModifiedAt: Long,
    val openedAt: Long,
    val pendingRecovery: Boolean = false
)

data class VaultOpenSessionFinalizeResult(
    val updated: Int,
    val cleaned: Int,
    val failed: Int
)

internal object VaultOpenSessionChangeDetector {
    fun isChanged(
        originalSize: Long,
        originalModifiedAt: Long,
        currentSize: Long,
        currentModifiedAt: Long
    ): Boolean = originalSize != currentSize || originalModifiedAt != currentModifiedAt
}

internal object VaultOpenSessionStore {
    private val sessionsBySessionId = LinkedHashMap<String, VaultOpenSession>()
    private val sessionIdByEntryId = LinkedHashMap<String, String>()

    @Synchronized
    fun add(session: VaultOpenSession) {
        sessionIdByEntryId.remove(session.entryId)?.let(sessionsBySessionId::remove)
        sessionsBySessionId[session.sessionId] = session
        sessionIdByEntryId[session.entryId] = session.sessionId
    }

    @Synchronized
    fun findBySessionId(sessionId: String): VaultOpenSession? = sessionsBySessionId[sessionId]

    @Synchronized
    fun removeBySessionId(sessionId: String): VaultOpenSession? {
        val removed = sessionsBySessionId.remove(sessionId) ?: return null
        sessionIdByEntryId.remove(removed.entryId)
        return removed
    }

    @Synchronized
    fun removeByEntryId(entryId: String): VaultOpenSession? {
        val sessionId = sessionIdByEntryId.remove(entryId) ?: return null
        return sessionsBySessionId.remove(sessionId)
    }

    @Synchronized
    fun updateBySessionId(sessionId: String, block: (VaultOpenSession) -> VaultOpenSession) {
        val current = sessionsBySessionId[sessionId] ?: return
        val updated = block(current)
        sessionsBySessionId[sessionId] = updated
        sessionIdByEntryId[updated.entryId] = updated.sessionId
    }

    @Synchronized
    fun forVault(vaultId: String): List<VaultOpenSession> =
        sessionsBySessionId.values.filter { it.vaultId == vaultId }

    @Synchronized
    fun allSessions(): List<VaultOpenSession> = sessionsBySessionId.values.toList()
}

class VaultOpenSessionManager(
    private val context: Context,
    private val vaultManager: VaultManager = VaultManager(context)
) {
    private val recoveryStore = VaultOpenSessionRecoveryStore(context)

    fun prepareOpenIntent(vaultId: String, entry: VaultEntry): Result<Intent> = runCatching {
        discardSession(vaultId, entry.id)
        val displayName = entry.name.ifBlank { "vault_entry" }
        val session = VaultOpenSession(
            sessionId = UUID.randomUUID().toString(),
            vaultId = vaultId,
            entryId = entry.id,
            displayName = displayName,
            mimeType = MimeType.guessFromPath(displayName),
            originalSize = entry.size,
            originalModifiedAt = entry.modifiedAt,
            openedAt = System.currentTimeMillis()
        )
        VaultOpenSessionStore.add(session)
        VaultOpenSessionUri.create(session).createViewIntent(session.mimeType)
    }

    fun finalizeSessionsForVault(vaultId: String): VaultOpenSessionFinalizeResult {
        var updated = 0
        var cleaned = 0
        var failed = 0

        for (session in recoveryStore.pendingRecoveries(vaultId)) {
            val replayed = runCatching {
                recoveryStore.replay(session) { input ->
                    vaultManager.replaceFile(vaultId, session.entryId, input).getOrThrow()
                }
            }
            if (replayed.isSuccess) {
                recoveryStore.delete(session.sessionId)
                VaultOpenSessionStore.removeBySessionId(session.sessionId)
                updated += 1
            } else {
                failed += 1
            }
        }

        for (session in VaultOpenSessionStore.forVault(vaultId)) {
            if (VaultOpenSessionAccess.isActive(session.sessionId)) {
                continue
            }
            if (session.pendingRecovery) {
                failed += 1
                continue
            }
            VaultOpenSessionStore.removeBySessionId(session.sessionId)
            recoveryStore.delete(session.sessionId)
            cleaned += 1
        }

        cleanupStaleSessions()
        recoveryStore.cleanupStaleRecoveries()
        return VaultOpenSessionFinalizeResult(updated = updated, cleaned = cleaned, failed = failed)
    }

    fun discardSession(vaultId: String, entryId: String) {
        val session = VaultOpenSessionStore.removeByEntryId(entryId) ?: return
        if (session.vaultId != vaultId) {
            VaultOpenSessionStore.add(session)
            return
        }
        if (!VaultOpenSessionAccess.isActive(session.sessionId)) {
            recoveryStore.delete(session.sessionId)
        }
    }

    private fun cleanupStaleSessions() {
        val cutoff = System.currentTimeMillis() - STALE_SESSION_MILLIS
        for (session in VaultOpenSessionStore.allSessions()) {
            if (
                session.openedAt < cutoff &&
                !VaultOpenSessionAccess.isActive(session.sessionId)
            ) {
                VaultOpenSessionStore.removeBySessionId(session.sessionId)
                recoveryStore.delete(session.sessionId)
            }
        }
    }

    private companion object {
        const val STALE_SESSION_MILLIS: Long = 15L * 60L * 1000L
    }
}
