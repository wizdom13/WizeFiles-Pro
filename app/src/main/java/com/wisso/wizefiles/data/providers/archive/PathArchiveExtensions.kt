package com.wisso.wizefiles.provider.archive

import com.wisso.wizefiles.storage.path.AppPath
import java.nio.file.Path
import java.nio.file.ProviderMismatchException

fun Path.archiveAddPassword(password: String) {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    fileSystem.addPassword(password)
}

val Path.archiveFile: AppPath
    get() {
        this as? ArchivePath ?: throw ProviderMismatchException(toString())
        return fileSystem.archiveFile
    }

fun Path.archiveRefresh() {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    fileSystem.refresh()
}

fun Path.createArchiveRootPath(): Path =
    ArchiveFileSystemProvider.getOrNewFileSystem(this).rootDirectory
