// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.File
import java.nio.file.Files
import org.json.JSONObject

internal object SyncthingVersionPolicy {
    fun restoreDelayMillis(versionTimes: List<String>, now: Long): Long =
        if (versionTimes.any { java.time.OffsetDateTime.parse(it).toInstant().epochSecond == now / 1000 })
            1100 - now % 1000 else 0

    fun keep(protection: String): Int {
        val json = JSONObject(protection)
        return if (json.optBoolean("enabled")) json.optInt("versionsPerFile", 5).coerceIn(1, 100) else 0
    }
    fun encode(previous: String, count: Int): String {
        require(count in 0..100)
        return JSONObject(previous).put("enabled", count > 0).put("versionsPerFile", count).toString()
    }
    fun safeFile(root: File, relative: String): File {
        require(relative.isNotBlank() && relative.length <= 4096 && !relative.contains('\\') &&
            relative.none(Char::isISOControl)) { "Invalid version path" }
        val path = java.nio.file.Paths.get(relative)
        require(!path.isAbsolute && path.none { it.toString() in setOf("..", ".") }) { "Invalid version path" }
        val base = root.canonicalFile.toPath()
        val target = base.resolve(path).normalize()
        require(target != base && target.startsWith(base)) { "Version path is outside its folder" }
        var current = base
        path.forEach { component ->
            current = current.resolve(component)
            require(!Files.isSymbolicLink(current)) { "Version path crosses a symbolic link" }
        }
        return target.toFile()
    }

    data class Conflicts(val names: List<String>, val truncated: Boolean)
    fun conflicts(root: File): Conflicts {
        val names = mutableListOf<String>()
        var visited = 0
        var truncated = false
        Files.walkFileTree(root.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun preVisitDirectory(dir: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                if (++visited > 100_000) { truncated = true; return java.nio.file.FileVisitResult.TERMINATE }
                return if (dir != root.toPath() && dir.fileName.toString() in setOf(".stversions", ".wizefiles-versions"))
                    java.nio.file.FileVisitResult.SKIP_SUBTREE else java.nio.file.FileVisitResult.CONTINUE
            }
            override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                if (++visited > 100_000 || names.size >= 1000) {
                    truncated = true
                    return java.nio.file.FileVisitResult.TERMINATE
                }
                if (attrs.isRegularFile && file.fileName.toString().contains(".sync-conflict-")) {
                    names.add(root.toPath().relativize(file).toString())
                }
                return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: java.nio.file.Path, exc: java.io.IOException): java.nio.file.FileVisitResult {
                truncated = true
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
        return Conflicts(names.sorted(), truncated)
    }
}
