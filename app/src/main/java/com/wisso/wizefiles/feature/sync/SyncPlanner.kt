package com.wisso.wizefiles.feature.sync

import kotlin.math.abs
import kotlin.math.max

internal data class SyncFileEntry(
    val relativePath: String,
    val uri: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val sizeBytes: Long = 0,
    val modifiedAtMillis: Long = 0,
    val modifiedPrecisionMillis: Long = 1_000,
    val revision: String = "",
    val checksum: String = "",
    val mimeType: String = ""
) {
    val normalizedRelativePath: String
        get() = relativePath.replace('\\', '/').trim('/')

    fun fingerprint(): String = buildString {
        append(if (isDirectory) "d" else "f")
        append(':').append(sizeBytes)
        append(':').append(modifiedAtMillis)
        if (revision.isNotEmpty()) append(":r=").append(revision)
        if (checksum.isNotEmpty()) append(":h=").append(checksum)
    }
}

internal data class SyncFilterRules(
    val includeHidden: Boolean = false,
    val includeSymlinks: Boolean = false,
    val minimumSizeBytes: Long = 0,
    val maximumSizeBytes: Long = Long.MAX_VALUE,
    val allowedExtensions: Set<String> = emptySet(),
    val excludedExtensions: Set<String> = emptySet(),
    val excludedPathPrefixes: Set<String> = emptySet(),
    val maximumDepth: Int = Int.MAX_VALUE
) {
    fun accepts(entry: SyncFileEntry): Boolean {
        val path = entry.normalizedRelativePath
        if (entry.isSymlink && !includeSymlinks) return false
        val segments = path.split('/')
        if (segments.any { it == ".wizefiles-versions" || it == ".wizefiles-sync" }) {
            return false
        }
        if (!includeHidden && path.split('/').any { it.startsWith('.') }) return false
        if (path.count { it == '/' } + 1 > maximumDepth) return false
        if (entry.sizeBytes !in minimumSizeBytes..maximumSizeBytes) return false
        if (excludedPathPrefixes.any { path == it || path.startsWith("$it/") }) return false
        val extension = path.substringAfterLast('.', "").lowercase()
        if (allowedExtensions.isNotEmpty() && extension !in allowedExtensions.map(String::lowercase)) {
            return false
        }
        if (extension in excludedExtensions.map(String::lowercase)) return false
        if (path.startsWith(".wizefiles-part-") || path.contains("/.wizefiles-part-")) return false
        return true
    }
}

internal data class SyncPlanSummary(
    val copiesToDestination: Long = 0,
    val copiesToSource: Long = 0,
    val updates: Long = 0,
    val moves: Long = 0,
    val protectedItems: Long = 0,
    val deletions: Long = 0,
    val conflicts: Long = 0,
    val unchangedOrSkipped: Long = 0,
    val transferBytes: Long = 0,
    val protectedBytes: Long = 0
)

internal data class SyncPlan(
    val actions: List<SyncAction>,
    val summary: SyncPlanSummary,
    val scanErrors: List<String> = emptyList()
) {
    val canExecuteDestructiveActions: Boolean
        get() = scanErrors.isEmpty()
}

internal object SyncEntryComparator {
    fun equivalent(
        source: SyncFileEntry,
        target: SyncFileEntry,
        policy: SyncComparisonPolicy
    ): Boolean {
        if (source.isDirectory != target.isDirectory) return false
        if (source.isDirectory) return true
        return when (policy) {
            SyncComparisonPolicy.SMART -> when {
                source.revision.isNotEmpty() && target.revision.isNotEmpty() ->
                    source.revision == target.revision
                source.checksum.isNotEmpty() && target.checksum.isNotEmpty() ->
                    source.checksum.equals(target.checksum, ignoreCase = true)
                else -> sameSizeAndModifiedTime(source, target)
            }
            SyncComparisonPolicy.SIZE_AND_MODIFIED_TIME -> sameSizeAndModifiedTime(source, target)
            SyncComparisonPolicy.SIZE_ONLY -> source.sizeBytes == target.sizeBytes
            SyncComparisonPolicy.CHECKSUM -> source.checksum.isNotEmpty() &&
                source.checksum.equals(target.checksum, ignoreCase = true)
        }
    }

    private fun sameSizeAndModifiedTime(first: SyncFileEntry, second: SyncFileEntry): Boolean {
        val tolerance = max(first.modifiedPrecisionMillis, second.modifiedPrecisionMillis)
        return first.sizeBytes == second.sizeBytes &&
            abs(first.modifiedAtMillis - second.modifiedAtMillis) <= tolerance
    }
}

internal class UpdateDestinationPlanner(
    private val comparisonPolicy: SyncComparisonPolicy,
    private val filters: SyncFilterRules = SyncFilterRules(),
    private val caseSensitive: Boolean = true
) {
    fun plan(
        runId: String,
        sourceEntries: Sequence<SyncFileEntry>,
        destinationEntries: Sequence<SyncFileEntry>,
        destinationRootUri: String,
        scanErrors: List<String> = emptyList()
    ): SyncPlan {
        val destination = destinationEntries
            .filter(filters::accepts)
            .associateBy { key(it.normalizedRelativePath) }
        val actions = mutableListOf<SyncAction>()
        var ordinal = 0L
        var copied = 0L
        var updated = 0L
        var skipped = 0L
        var bytes = 0L
        sourceEntries.forEach { source ->
            if (!filters.accepts(source)) {
                skipped++
                return@forEach
            }
            val target = destination[key(source.normalizedRelativePath)]
            val type = when {
                target == null -> SyncActionType.COPY
                SyncEntryComparator.equivalent(source, target, comparisonPolicy) -> SyncActionType.SKIP
                else -> SyncActionType.UPDATE
            }
            if (type == SyncActionType.COPY) copied++
            if (type == SyncActionType.UPDATE) updated++
            if (type == SyncActionType.SKIP) skipped++ else bytes += source.sizeBytes
            actions += SyncAction(
                runId = runId,
                ordinal = ordinal++,
                type = type,
                direction = SyncSide.DESTINATION,
                relativePath = source.normalizedRelativePath,
                sourceUri = source.uri,
                targetUri = resolve(destinationRootUri, source.normalizedRelativePath),
                sourceFingerprint = source.fingerprint(),
                targetFingerprint = target?.fingerprint().orEmpty(),
                comparisonReason = when (type) {
                    SyncActionType.COPY -> "DESTINATION_MISSING"
                    SyncActionType.UPDATE -> "SOURCE_CHANGED"
                    else -> "UNCHANGED"
                },
                state = if (type == SyncActionType.SKIP) {
                    SyncActionState.SKIPPED
                } else {
                    SyncActionState.PENDING
                }
            )
        }
        return SyncPlan(
            actions = actions,
            summary = SyncPlanSummary(
                copiesToDestination = copied,
                updates = updated,
                unchangedOrSkipped = skipped,
                transferBytes = bytes
            ),
            scanErrors = scanErrors
        )
    }

    private fun key(path: String): String = if (caseSensitive) path else path.lowercase()

    private fun resolve(rootUri: String, relativePath: String): String =
        SyncPathResolver.childUri(rootUri, relativePath)
}
