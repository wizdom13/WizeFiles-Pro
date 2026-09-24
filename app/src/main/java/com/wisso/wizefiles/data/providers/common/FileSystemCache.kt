// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystemNotFoundException
import java.lang.ref.WeakReference

class FileSystemCache<K : Any, FS : FileSystem?> {
    private val fileSystems: MutableMap<K, WeakReference<FS>> = HashMap()

    private val lock = Any()

    fun create(key: K, fileSystemCreator: () -> FS): FS {
        synchronized(lock) {
            var fileSystem = fileSystems[key]?.get()
            if (fileSystem != null) {
                throw FileSystemAlreadyExistsException(key.toString())
            }
            fileSystem = fileSystemCreator()
            fileSystems[key] = WeakReference(fileSystem)
            return fileSystem
        }
    }

    fun getOrCreate(key: K, fileSystemCreator: () -> FS): FS {
        synchronized(lock) {
            var fileSystem = fileSystems[key]?.get()
            if (fileSystem != null) {
                return fileSystem
            }
            fileSystem = fileSystemCreator()
            fileSystems[key] = WeakReference(fileSystem)
            return fileSystem
        }
    }

    operator fun get(key: K): FS {
        synchronized(lock) {
            val fileSystem = fileSystems[key]?.get()
            if (fileSystem == null) {
                fileSystems.remove(key)
                throw FileSystemNotFoundException(key.toString())
            }
            return fileSystem
        }
    }

    fun remove(key: K, fileSystem: FS) {
        synchronized(lock) {
            val fileSystemReference = fileSystems[key] ?: return
            val currentFileSystem = fileSystemReference.get()
            if (currentFileSystem == null || currentFileSystem == fileSystem) {
                fileSystems.remove(key)
            }
        }
    }
}
