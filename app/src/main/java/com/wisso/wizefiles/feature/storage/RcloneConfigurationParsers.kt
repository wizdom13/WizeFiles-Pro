package com.wisso.wizefiles.storage

internal data class ImportedRemote(val name: String, val type: String)

internal fun parseImportedRemotes(configuration: String): List<ImportedRemote> {
    val section = Regex("""(?m)^\s*\[([^\]]+)]\s*$""")
    val matches = section.findAll(configuration).toList()
    return matches.mapNotNull { match ->
        val start = match.range.last + 1
        val end = matches.firstOrNull { it.range.first > start }?.range?.first
            ?: configuration.length
        val body = configuration.substring(start, end)
        val type = Regex("""(?m)^\s*type\s*=\s*(\S+)\s*$""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: return@mapNotNull null
        ImportedRemote(match.groupValues[1], type)
    }
}

internal fun parseAdvancedOptions(value: String): List<Pair<String, String>> =
    value.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null
            else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
        .toList()
