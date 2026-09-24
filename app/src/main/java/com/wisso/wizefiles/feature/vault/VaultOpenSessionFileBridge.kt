// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.core.app.storageManager
import com.wisso.wizefiles.core.android.compat.ProxyFileDescriptorCallbackCompat
import com.wisso.wizefiles.core.android.compat.openProxyFileDescriptorCompat
import com.wisso.wizefiles.core.files.provider.legacy.coerceLegacyOpenMode
import com.wisso.wizefiles.core.files.provider.legacy.toLegacyOpenMode
import com.wisso.wizefiles.util.hasBits
import java.io.FileNotFoundException

internal object VaultOpenSessionUri {
    private const val SESSION_SEGMENT = "vault-session"

    fun create(session: VaultOpenSession): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(BuildConfig.FILE_PROVIDER_AUTHORITY)
            .appendPath(SESSION_SEGMENT)
            .appendPath(session.sessionId)
            .appendPath(session.displayName)
            .build()

    fun isVaultOpenSessionUri(uri: Uri): Boolean =
        uri.authority == BuildConfig.FILE_PROVIDER_AUTHORITY &&
            uri.pathSegments.firstOrNull() == SESSION_SEGMENT

    fun getSessionId(uri: Uri): String? =
        uri.pathSegments.takeIf { isVaultOpenSessionUri(uri) }?.getOrNull(1)
}

internal object VaultOpenSessionFileBridge {
    fun query(uri: Uri, projection: Array<String?>?): Cursor? {
        val session = resolveSession(uri) ?: return null
        val projectionColumns = projection ?: DEFAULT_PROJECTION
        val columns = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        for (column in projectionColumns) {
            when (column) {
                OpenableColumns.DISPLAY_NAME -> {
                    columns += column
                    values += session.displayName
                }
                OpenableColumns.SIZE -> {
                    columns += column
                    values += session.originalSize
                }
                DocumentsContract.Document.COLUMN_MIME_TYPE -> {
                    columns += column
                    values += session.mimeType.value
                }
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> {
                    columns += column
                    values += session.originalModifiedAt
                }
                MediaStore.MediaColumns.DATA -> {
                    // Vault open sessions are virtual; there is no direct filesystem path.
                }
            }
        }
        return MatrixCursor(columns.toTypedArray(), 1).apply {
            addRow(values)
        }
    }

    fun getType(uri: Uri): String? = resolveSession(uri)?.mimeType?.value

    @Throws(FileNotFoundException::class)
    fun openFile(context: Context, uri: Uri, mode: String, callbackHandler: Handler): ParcelFileDescriptor {
        val session = resolveSession(uri) ?: throw FileNotFoundException("Missing vault open session")
        val parsedMode = mode.toLegacyOpenMode()
        val modeBits = coerceLegacyOpenMode(parsedMode, canRead = true, canWrite = true)
        val writable = modeBits.hasBits(ParcelFileDescriptor.MODE_WRITE_ONLY) ||
            modeBits.hasBits(ParcelFileDescriptor.MODE_READ_WRITE)
        val truncateRequested = modeBits.hasBits(ParcelFileDescriptor.MODE_TRUNCATE)

        return VaultOpenSessionAccess.withLock(session.sessionId) {
            val manager = VaultManager(context)
            val key = manager.copyUnlockedKey(session.vaultId)
            val existingStore = try {
                VaultOpenSessionEncryptedStore.open(context, session, key)
            } finally {
                VaultCrypto.zero(key)
            }
            val store = existingStore ?: createSeededStore(context, session, manager, truncateRequested)
            if (existingStore != null && truncateRequested) {
                store.truncate(0L)
            }
            VaultOpenSessionAccess.opened(session.sessionId, dirty = truncateRequested)
            try {
                storageManager.openProxyFileDescriptorCompat(
                    modeBits,
                    VaultOpenSessionEncryptedCallback(
                        context = context,
                        session = session,
                        store = store,
                        writable = writable
                    ),
                    callbackHandler
                )
            } catch (throwable: Throwable) {
                VaultOpenSessionAccess.released(session.sessionId)
                store.close()
                throw throwable
            }
        }
    }

    private fun createSeededStore(
        context: Context,
        session: VaultOpenSession,
        manager: VaultManager,
        truncateRequested: Boolean
    ): VaultOpenSessionEncryptedStore {
        val key = manager.copyUnlockedKey(session.vaultId)
        val store = try {
            VaultOpenSessionEncryptedStore.create(context, session, key)
        } finally {
            VaultCrypto.zero(key)
        }
        if (truncateRequested) return store
        return try {
            store.openReplacingOutputStream().use { output ->
                manager.copyFileTo(session.vaultId, session.entryId, output)
            }
            store
        } catch (throwable: Throwable) {
            store.delete()
            throw throwable
        }
    }

    private fun resolveSession(uri: Uri): VaultOpenSession? {
        val sessionId = VaultOpenSessionUri.getSessionId(uri) ?: return null
        return VaultOpenSessionStore.findBySessionId(sessionId)
    }

    private val DEFAULT_PROJECTION = arrayOf(
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
}

private class VaultOpenSessionEncryptedCallback(
    private val context: Context,
    private val session: VaultOpenSession,
    private val store: VaultOpenSessionEncryptedStore,
    private val writable: Boolean
) : ProxyFileDescriptorCallbackCompat() {
    private var released = false

    @Throws(ErrnoException::class)
    override fun onGetSize(): Long = VaultOpenSessionAccess.withLock(session.sessionId) {
        ensureNotReleased()
        store.size()
    }

    @Throws(ErrnoException::class)
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int =
        VaultOpenSessionAccess.withLock(session.sessionId) {
            ensureNotReleased()
            if (offset < 0L) throw ErrnoException("onRead", OsConstants.EINVAL)
            runCatching { store.read(offset, size, data) }
                .getOrElse { throw ErrnoException("onRead", OsConstants.EIO) }
        }

    @Throws(ErrnoException::class)
    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int =
        VaultOpenSessionAccess.withLock(session.sessionId) {
            ensureNotReleased()
            if (!writable) throw ErrnoException("onWrite", OsConstants.EBADF)
            if (offset < 0L) throw ErrnoException("onWrite", OsConstants.EINVAL)
            val written = runCatching { store.write(offset, size, data) }
                .getOrElse { throw ErrnoException("onWrite", OsConstants.EIO) }
            VaultOpenSessionAccess.markDirty(session.sessionId)
            written
        }

    @Throws(ErrnoException::class)
    override fun onFsync() {
        VaultOpenSessionAccess.withLock(session.sessionId) {
            ensureNotReleased()
        }
    }

    override fun onRelease() {
        VaultOpenSessionAccess.withLock(session.sessionId) {
            if (released) return@withLock
            released = true
            val release = VaultOpenSessionAccess.released(session.sessionId)
            if (!release.isLast) {
                store.close()
                return@withLock
            }

            try {
                if (release.isDirty) {
                    val replaceResult = store.openInputStream().use { input ->
                        VaultManager(context).replaceFile(session.vaultId, session.entryId, input)
                    }
                    if (replaceResult.isSuccess) {
                        VaultOpenSessionStore.removeBySessionId(session.sessionId)
                        store.delete()
                    } else {
                        store.markPendingRecovery()
                        VaultOpenSessionStore.updateBySessionId(session.sessionId) {
                            it.copy(pendingRecovery = true)
                        }
                        store.close()
                    }
                } else {
                    VaultOpenSessionStore.removeBySessionId(session.sessionId)
                    store.delete()
                }
            } catch (_: Throwable) {
                runCatching { store.markPendingRecovery() }
                VaultOpenSessionStore.updateBySessionId(session.sessionId) {
                    it.copy(pendingRecovery = true)
                }
                store.close()
            } finally {
                VaultOpenSessionAccess.finished(session.sessionId)
            }
        }
    }

    @Throws(ErrnoException::class)
    private fun ensureNotReleased() {
        if (released) throw ErrnoException(null, OsConstants.EBADF)
    }
}

internal class VaultOpenSessionRecoveryStore(private val context: Context) {
    fun pendingRecoveries(vaultId: String): List<VaultOpenSession> =
        VaultOpenSessionEncryptedStore.pendingSessions(context, vaultId)

    fun replay(session: VaultOpenSession, block: (java.io.InputStream) -> Unit) {
        val key = VaultManager(context).copyUnlockedKey(session.vaultId)
        val store = try {
            requireNotNull(VaultOpenSessionEncryptedStore.open(context, session, key)) {
                "Missing encrypted Vault recovery store"
            }
        } finally {
            VaultCrypto.zero(key)
        }
        try {
            store.openInputStream().use(block)
        } finally {
            store.close()
        }
    }

    fun delete(sessionId: String) {
        VaultOpenSessionEncryptedStore.delete(context, sessionId)
    }

    fun cleanupStaleRecoveries() {
        VaultOpenSessionEncryptedStore.cleanupStale(context)
    }
}
