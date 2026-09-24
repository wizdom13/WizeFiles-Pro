package com.wisso.wizefiles.storage

import com.wisso.wizefiles.provider.rclone.RcloneEngine
import com.wisso.wizefiles.provider.rclone.RcloneProviderDefinition

internal object RcloneStorageRepository {
    fun providerDefinitions(): List<RcloneProviderDefinition> = RcloneEngine.listProviders()
    fun importRemote(configuration: String, source: String, target: String) =
        RcloneEngine.importRemote(configuration, source, target)
    fun verify(remote: String, rootPath: String) = RcloneEngine.list(remote, rootPath)
    fun deleteRemote(remote: String) = RcloneEngine.deleteRemote(remote)
    fun save(storage: RcloneStorage) = Storages.addOrReplace(storage)
    fun remove(storage: RcloneStorage) = Storages.remove(storage)
}
