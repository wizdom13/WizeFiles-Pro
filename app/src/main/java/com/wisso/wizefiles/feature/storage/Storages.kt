// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import com.wisso.wizefiles.provider.ftp.client.FtpClient as FtpConnectionPool
import com.wisso.wizefiles.provider.sftp.client.SftpClient as SftpConnectionPool
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.removeFirst
import com.wisso.wizefiles.util.valueCompat

object Storages {
    fun canAddRemoteStorage(): Boolean = true

    fun canAddVault(): Boolean = true

    /**
     * Adds or replaces storage entries without commercial account-count limits.
     */
    fun addOrReplace(storage: Storage): Boolean {
        val current = Settings.STORAGES.valueCompat
        val isNew = current.none { it.id == storage.id }
        if (isNew && storage.isRemoteConnection() && !canAddRemoteStorage()) return false
        if (isNew && storage is VaultStorage && !canAddVault()) return false

        var replaced: Storage? = null
        val storages = current.toMutableList().apply {
            val index = indexOfFirst { it.id == storage.id }
            if (index != -1) {
                replaced = this[index]
                this[index] = storage
            } else {
                this += storage
            }
        }
        Settings.STORAGES.putValue(storages)
        replaced?.let(::closeConnections)
        return true
    }

    fun replace(storage: Storage) {
        var replaced: Storage? = null
        val storages = Settings.STORAGES.valueCompat.toMutableList().apply {
            val index = indexOfFirst { it.id == storage.id }
            replaced = this[index]
            this[index] = storage
        }
        Settings.STORAGES.putValue(storages)
        replaced?.let(::closeConnections)
    }

    fun move(fromPosition: Int, toPosition: Int) {
        val bookmarkDirectories = Settings.STORAGES.valueCompat.toMutableList()
            .apply { add(toPosition, removeAt(fromPosition)) }
        Settings.STORAGES.putValue(bookmarkDirectories)
    }

    fun remove(storage: Storage) {
        val bookmarkDirectories = Settings.STORAGES.valueCompat.toMutableList()
            .apply { removeFirst { it.id == storage.id } }
        Settings.STORAGES.putValue(bookmarkDirectories)
        closeConnections(storage)
    }

    private fun closeConnections(storage: Storage) {
        when (storage) {
            is FtpServer -> FtpConnectionPool.close(storage.authority)
            is SftpServer -> SftpConnectionPool.close(storage.authority)
            else -> Unit
        }
    }

    private fun Storage.isRemoteConnection(): Boolean =
        this is RcloneStorage || this is FtpServer || this is SftpServer || this is SmbServer

}
