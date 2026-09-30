// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about.changelog

internal data class ChangelogRelease(val version: String, val order: Int, val html: String)
internal data class ChangelogNotice(val version: String, val order: Int, val html: String)

/** Reads the deliberately small section format emitted by generate-changelogs.py. */
internal class ChangelogCatalog private constructor(private val releases: List<ChangelogRelease>) {
    fun notice(versionName: String, lastSeenOrder: Int): ChangelogNotice? {
        val current = releases.firstOrNull {
            versionName == it.version || versionName.startsWith("${it.version}-")
        } ?: return null
        if (lastSeenOrder >= current.order) return null
        val unseen = if (lastSeenOrder <= 0) listOf(current) else {
            releases.filter { it.order in (lastSeenOrder + 1)..current.order }
        }
        return ChangelogNotice(current.version, current.order, unseen.joinToString("\n") { it.html })
    }

    companion object {
        fun parse(html: String): ChangelogCatalog {
            val sections = Regex("<section\\b([^>]*)>(.*?)</section>", RegexOption.DOT_MATCHES_ALL)
            val releases = sections.findAll(html).mapNotNull { section ->
                val attributes = section.groupValues[1]
                val version = Regex("data-version=\"([0-9]+(?:\\.[0-9]+)+)\"")
                    .find(attributes)?.groupValues?.get(1) ?: return@mapNotNull null
                val order = Regex("data-release-order=\"([0-9]+)\"")
                    .find(attributes)?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: return@mapNotNull null
                val body = section.groupValues[2].trim()
                if (!body.contains("<li>")) return@mapNotNull null
                ChangelogRelease(version, order, body)
            }.toList().sortedByDescending { it.order }
            require(releases.map { it.order }.distinct().size == releases.size) { "Duplicate release order" }
            require(releases.map { it.version }.distinct().size == releases.size) { "Duplicate release version" }
            return ChangelogCatalog(releases)
        }
    }
}
