// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.editor

import java.io.IOException
import java.util.Locale

object ArchiveMutationPlanner {
    @Throws(IOException::class)
    fun plan(
        existing: List<ArchiveNamespaceEntry>,
        mutations: List<ArchiveMutation>,
        additions: List<PlannedArchiveEntry> = emptyList(),
        conflictPolicy: ArchiveConflictPolicy = ArchiveConflictPolicy.FAIL
    ): ArchiveMutationPlan {
        val entries = LinkedHashMap<String, PlannedArchiveEntry>()
        existing.forEach { entry ->
            val normalized = normalize(entry.path, entry.isDirectory)
            if (normalized.isEmpty()) return@forEach
            putUnique(
                entries,
                PlannedArchiveEntry(normalized, normalized, entry.isDirectory, size = entry.size)
            )
        }
        val skipped = mutableListOf<String>()

        mutations.filter { it.type == ArchiveMutationType.DELETE }.forEach { mutation ->
            val path = normalize(mutation.path, false)
            entries.keys.filter { isSameOrDescendant(it, path) }.forEach(entries::remove)
        }

        mutations.filter { it.type == ArchiveMutationType.RENAME }.forEach { mutation ->
            val source = normalize(mutation.path, false)
            val target = normalize(mutation.targetPath, false)
            if (source == target) return@forEach
            val affected = entries.values.filter {
                it.originalPath?.let { original -> isSameOrDescendant(original, source) } == true
            }
            if (affected.isEmpty()) throw IOException("Archive entry no longer exists: $source")
            val unaffectedKeys = entries.keys.filterNot { key -> isSameOrDescendant(key, source) }.toSet()
            val remapped = affected.map { entry ->
                val suffix = entry.finalPath.removePrefix(source).removePrefix("/")
                val finalPath = if (suffix.isEmpty()) target else "$target/$suffix"
                entry.copy(finalPath = finalPath)
            }
            remapped.forEach { entry ->
                if (unaffectedKeys.any { key -> key.equals(entry.finalPath, ignoreCase = true) }) {
                    throw IOException("Rename target already exists: ${entry.finalPath}")
                }
            }
            entries.keys.filter { key -> isSameOrDescendant(key, source) }.forEach(entries::remove)
            remapped.forEach { putUnique(entries, it) }
        }

        mutations.filter { it.type == ArchiveMutationType.CREATE_DIRECTORY }.forEach { mutation ->
            val path = normalize(mutation.path, true)
            addWithConflict(
                entries,
                PlannedArchiveEntry(null, path, true),
                conflictPolicy,
                skipped
            )
        }

        val additionRemaps = mutableListOf<Pair<String, String>>()
        val skippedDirectories = mutableListOf<String>()
        additions.sortedBy { it.finalPath.count { character -> character == '/' } }.forEach { addition ->
            val original = normalize(addition.finalPath, addition.isDirectory)
            if (skippedDirectories.any { isSameOrDescendant(original, it) }) {
                skipped += original
                return@forEach
            }
            val remap = additionRemaps
                .filter { (from, _) -> isSameOrDescendant(original, from) }
                .maxByOrNull { (from, _) -> from.length }
            val finalPath = if (remap == null) {
                original
            } else {
                remap.second + original.removePrefix(remap.first)
            }
            val actual = addWithConflict(
                entries,
                addition.copy(finalPath = finalPath),
                conflictPolicy,
                skipped
            )
            if (addition.isDirectory) {
                if (actual == null) skippedDirectories += original
                else if (actual != original) additionRemaps += original to actual
            }
        }

        validateHierarchy(entries.values)
        return ArchiveMutationPlan(entries.values.toList(), skipped)
    }

    @Throws(IOException::class)
    fun normalize(path: String, isDirectory: Boolean): String {
        if (path.indexOf('\u0000') >= 0 || path.indexOf('\uFFFD') >= 0) {
            throw IOException("Unsafe archive entry name")
        }
        val replaced = path.trim().replace('\\', '/').trim('/')
        val segments = mutableListOf<String>()
        replaced.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> throw IOException("Unsafe archive entry traversal")
                else -> segments += segment
            }
        }
        if (segments.isEmpty() && !isDirectory) throw IOException("Unsafe archive entry name")
        return segments.joinToString("/")
    }

    private fun addWithConflict(
        entries: LinkedHashMap<String, PlannedArchiveEntry>,
        incoming: PlannedArchiveEntry,
        policy: ArchiveConflictPolicy,
        skipped: MutableList<String>
    ): String? {
        val occupied = entries.keys.firstOrNull { it.equals(incoming.finalPath, ignoreCase = true) }
        if (occupied == null) {
            putUnique(entries, incoming)
            return incoming.finalPath
        }
        return when (policy) {
            ArchiveConflictPolicy.FAIL -> throw IOException("Archive entry already exists: ${incoming.finalPath}")
            ArchiveConflictPolicy.SKIP -> {
                skipped += incoming.finalPath
                null
            }
            ArchiveConflictPolicy.REPLACE -> {
                entries.remove(occupied)
                if (incoming.isDirectory) {
                    putUnique(entries, incoming)
                } else {
                    entries.keys.filter { isSameOrDescendant(it, occupied) }.forEach(entries::remove)
                    putUnique(entries, incoming)
                }
                incoming.finalPath
            }
            ArchiveConflictPolicy.KEEP_BOTH -> {
                val kept = keepBothName(entries.keys, incoming.finalPath)
                putUnique(entries, incoming.copy(finalPath = kept))
                kept
            }
        }
    }

    private fun keepBothName(existing: Set<String>, requested: String): String {
        val slash = requested.lastIndexOf('/')
        val parent = if (slash >= 0) requested.substring(0, slash + 1) else ""
        val name = requested.substring(slash + 1)
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val extension = name.substring(dot)
        var index = 2
        while (true) {
            val candidate = "$parent$base ($index)$extension"
            if (existing.none { it.equals(candidate, ignoreCase = true) }) return candidate
            index++
        }
    }

    private fun validateHierarchy(entries: Collection<PlannedArchiveEntry>) {
        val files = entries.filterNot { it.isDirectory }.map { key(it.finalPath) }.toSet()
        entries.forEach { entry ->
            var parent = entry.finalPath.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                if (key(parent) in files) throw IOException("File and folder collision: $parent")
                parent = parent.substringBeforeLast('/', "")
            }
        }
    }

    private fun putUnique(
        entries: LinkedHashMap<String, PlannedArchiveEntry>,
        entry: PlannedArchiveEntry
    ) {
        val duplicate = entries.keys.firstOrNull { it.equals(entry.finalPath, ignoreCase = true) }
        if (duplicate != null) throw IOException("Duplicate archive entry path: ${entry.finalPath}")
        entries[entry.finalPath] = entry
    }

    private fun isSameOrDescendant(candidate: String, parent: String): Boolean =
        candidate.equals(parent, ignoreCase = true) ||
            candidate.lowercase(Locale.ROOT).startsWith(parent.lowercase(Locale.ROOT) + "/")

    private fun key(path: String): String = path.lowercase(Locale.ROOT)
}
