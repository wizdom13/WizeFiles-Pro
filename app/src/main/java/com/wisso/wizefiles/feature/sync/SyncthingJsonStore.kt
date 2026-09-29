// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/** Small app-private documents, replaced atomically; shared locking covers separate store callers. */
internal class SyncthingJsonStore(private val file: File) {
    private val lock = locks.getOrPut(file.canonicalPath) { Any() }

    fun read(): JSONObject = synchronized(lock) { readLocked() }

    fun update(change: (JSONObject) -> Unit) = synchronized(lock) {
        val json = readLocked()
        change(json)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val pending = File(file.parentFile, file.name + ".pending")
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Syncthing settings exceed the storage limit" }
        FileOutputStream(pending).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
    }

    private fun readLocked(): JSONObject {
        if (!file.exists()) return JSONObject()
        check(file.length() <= MAX_BYTES) { "Syncthing settings exceed the storage limit" }
        // A corrupt document must not be replaced silently with empty configuration.
        return JSONObject(file.readText())
    }

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
