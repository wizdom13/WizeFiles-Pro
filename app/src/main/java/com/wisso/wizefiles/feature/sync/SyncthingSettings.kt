// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.core.app.application
import java.io.File
import kotlinx.coroutines.sync.Mutex

internal object SyncthingSettings {
    val configuration get() = SyncthingConfiguration(SyncthingJsonStore(
        File(application.noBackupFilesDir, "syncthing-state/configuration.json")))

    fun migrate() {
        SyncthingMigration.recover(application)
        check(!SyncthingMigration.applying) { "Syncthing configuration import is in progress" }
        SyncRepository.profiles().forEach { profile ->
            SyncthingEndpointCodec.decode(profile.destinationUri)?.let {
                configuration.migrate(profile.id, it.deviceId)
            }
        }
    }
}

/** Configuration and recovery operations cannot overlap a folder run. Status reads may. */
internal object SyncthingCoordinator {
    val execution = Mutex()
    suspend fun <T> whileIdle(block: suspend () -> T): T {
        check(execution.tryLock()) { "Stop the current synchronization before changing Syncthing settings" }
        return try { block() } finally { execution.unlock() }
    }
}
