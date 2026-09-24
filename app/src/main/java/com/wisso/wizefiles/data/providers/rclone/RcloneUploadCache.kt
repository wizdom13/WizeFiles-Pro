package com.wisso.wizefiles.provider.rclone

import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.CopyOnWriteArraySet

internal enum class RcloneUploadDisplayState {
    QUEUED,
    COPYING
}

internal data class RcloneUploadPlanEntry(
    val path: Path,
    val attributes: BasicFileAttributes
)

/**
 * Lightweight upload plans published by a copy job before any staging files are created.
 */
internal class RcloneUploadPlanOverlay {
    private data class PlannedUpload(
        val path: RclonePath,
        val attributes: RcloneFileAttributes,
        val state: RcloneUploadDisplayState
    )

    private val lock = Any()
    private val plans = linkedMapOf<String, PlannedUpload>()
    private val aliases = linkedMapOf<String, String>()

    fun plan(entries: Iterable<RcloneUploadPlanEntry>): Boolean {
        var changed = false
        synchronized(lock) {
            for (entry in entries) {
                val path = entry.path.normalizedRclonePath ?: continue
                val key = path.uploadCacheKey
                val attributes = RcloneFileAttributes(
                    path = path.toString(),
                    directory = entry.attributes.isDirectory,
                    length = entry.attributes.size(),
                    modifiedAt = entry.attributes.lastModifiedTime().toInstant()
                )
                plans[key] = PlannedUpload(path, attributes, RcloneUploadDisplayState.QUEUED)
                aliases[key] = key
                changed = true
            }
        }
        return changed
    }

    fun markCopying(path: Path): Boolean {
        val rclonePath = path.normalizedRclonePath ?: return false
        return synchronized(lock) {
            val key = resolveKey(rclonePath.uploadCacheKey)
            val current = plans[key] ?: return@synchronized false
            if (current.state == RcloneUploadDisplayState.COPYING) {
                false
            } else {
                plans[key] = current.copy(state = RcloneUploadDisplayState.COPYING)
                true
            }
        }
    }

    fun move(from: Path, to: Path): Boolean {
        val fromPath = from.normalizedRclonePath ?: return false
        val toPath = to.normalizedRclonePath ?: return false
        return synchronized(lock) {
            val fromKey = resolveKey(fromPath.uploadCacheKey)
            val current = plans.remove(fromKey) ?: return@synchronized false
            val toKey = toPath.uploadCacheKey
            plans[toKey] = current.copy(
                path = toPath,
                attributes = RcloneFileAttributes(
                    path = toPath.toString(),
                    directory = current.attributes.isDirectory,
                    length = current.attributes.size(),
                    modifiedAt = current.attributes.lastModifiedTime().toInstant()
                )
            )
            aliases.entries.forEach { entry ->
                if (entry.value == fromKey) entry.setValue(toKey)
            }
            aliases.putIfAbsent(fromPath.uploadCacheKey, toKey)
            true
        }
    }

    fun complete(path: Path): RcloneListedPath? {
        val rclonePath = path.normalizedRclonePath ?: return null
        return synchronized(lock) {
            val key = resolveKey(rclonePath.uploadCacheKey)
            val completed = plans.remove(key) ?: return@synchronized null
            aliases.entries.removeAll { it.value == key }
            RcloneListedPath(completed.path, completed.attributes)
        }
    }

    fun remove(paths: Iterable<Path>): Boolean {
        var changed = false
        synchronized(lock) {
            for (path in paths) {
                val rclonePath = path.normalizedRclonePath ?: continue
                val key = resolveKey(rclonePath.uploadCacheKey)
                changed = plans.remove(key) != null || changed
                aliases.entries.removeAll { it.value == key }
            }
        }
        return changed
    }

    fun pendingChildren(directory: RclonePath): List<RcloneListedPath> {
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        return synchronized(lock) {
            plans.values
                .filter { it.path.parent?.toAbsolutePath()?.normalize() == normalizedDirectory }
                .map { RcloneListedPath(it.path, it.attributes) }
        }
    }

    fun state(path: Path): RcloneUploadDisplayState? {
        val rclonePath = path.normalizedRclonePath ?: return null
        return synchronized(lock) { plans[rclonePath.uploadCacheKey]?.state }
    }

    fun attributes(path: RclonePath): RcloneFileAttributes? = synchronized(lock) {
        plans[path.uploadCacheKey]?.attributes
    }

    private fun resolveKey(key: String): String = aliases[key] ?: key
}

/**
 * Process-local view of queued and active rclone uploads.
 *
 * The copy job owns lightweight queued plans. Rclone staging owns real local files and upload
 * completion. The browser merges both without treating placeholders as committed remote data.
 */
internal object RcloneUploadCache {
    private data class PendingUpload(
        val path: RclonePath,
        val stagingFile: File,
        val registeredAtMillis: Long,
        var readable: Boolean = false
    )

    private val lock = Any()
    private val uploads = linkedMapOf<String, PendingUpload>()
    private val plans = RcloneUploadPlanOverlay()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Handler(Looper.getMainLooper())
    }

    fun plan(entries: Iterable<RcloneUploadPlanEntry>) {
        if (plans.plan(entries)) notifyChanged()
    }

    fun markPlannedCopying(path: Path) {
        if (plans.markCopying(path)) notifyChanged()
    }

    fun movePlan(from: Path, to: Path) {
        if (plans.move(from, to)) notifyChanged()
    }

    fun completePlan(path: Path) {
        val completed = plans.complete(path) ?: return
        RcloneFileSystemProvider.cacheCompletedUpload(completed)
        notifyChanged()
    }

    fun removePlans(paths: Iterable<Path>) {
        if (plans.remove(paths)) notifyChanged()
    }

    fun register(path: RclonePath, stagingFile: File) {
        synchronized(lock) {
            uploads[path.uploadCacheKey] = PendingUpload(
                path,
                stagingFile,
                System.currentTimeMillis()
            )
        }
        notifyChanged()
    }

    fun markReadable(path: RclonePath, stagingFile: File) {
        synchronized(lock) {
            uploads[path.uploadCacheKey]
                ?.takeIf { it.stagingFile == stagingFile }
                ?.readable = true
        }
        notifyChanged()
    }

    fun complete(path: RclonePath, stagingFile: File) {
        val completed = snapshot(path, stagingFile)
        if (completed != null && !completed.path.isInternalTransferTarget) {
            RcloneFileSystemProvider.cacheCompletedUpload(completed)
        }
        val removedUpload = removeActive(path, stagingFile)
        val removedPlan = plans.remove(listOf(path))
        if (removedUpload || removedPlan) notifyChanged()
    }

    fun remove(path: RclonePath, stagingFile: File) {
        if (removeActive(path, stagingFile)) notifyChanged()
    }

    fun pendingChildren(directory: RclonePath): List<RcloneListedPath> {
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        val active = synchronized(lock) {
            uploads.values
                .filter {
                    !it.path.isInternalTransferTarget &&
                        it.path.parent?.toAbsolutePath()?.normalize() == normalizedDirectory
                }
                .map(::toListedPath)
        }
        return mergeRcloneListedPaths(plans.pendingChildren(directory), active)
    }

    fun displayState(path: Path): RcloneUploadDisplayState? {
        val rclonePath = path.normalizedRclonePath ?: return null
        val isActive = synchronized(lock) { rclonePath.uploadCacheKey in uploads }
        return if (isActive) RcloneUploadDisplayState.COPYING else plans.state(rclonePath)
    }

    fun isPending(path: Path): Boolean = displayState(path) != null

    @Throws(IOException::class)
    fun openInput(path: RclonePath): InputStream? {
        val upload = synchronized(lock) { uploads[path.uploadCacheKey] }
        if (upload != null) {
            if (!upload.readable) {
                throw IOException("The cloud file is still being prepared locally")
            }
            return upload.stagingFile.inputStream()
        }
        if (plans.state(path) != null) {
            throw IOException("The cloud file is queued for upload")
        }
        return null
    }

    fun attributes(path: RclonePath): RcloneFileAttributes? {
        val active = synchronized(lock) {
            uploads[path.uploadCacheKey]?.let(::toListedPath)?.attributes as? RcloneFileAttributes
        }
        return active ?: plans.attributes(path)
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun snapshot(path: RclonePath, stagingFile: File): RcloneListedPath? =
        synchronized(lock) {
            uploads[path.uploadCacheKey]
                ?.takeIf { it.stagingFile == stagingFile }
                ?.let(::toListedPath)
        }

    private fun removeActive(path: RclonePath, stagingFile: File): Boolean =
        synchronized(lock) {
            val current = uploads[path.uploadCacheKey]
            if (current?.stagingFile == stagingFile) {
                uploads.remove(path.uploadCacheKey)
                true
            } else {
                false
            }
        }

    private fun toListedPath(upload: PendingUpload): RcloneListedPath = RcloneListedPath(
        path = upload.path,
        attributes = RcloneFileAttributes(
            path = upload.path.toString(),
            directory = false,
            length = upload.stagingFile.length(),
            modifiedAt = Instant.ofEpochMilli(
                upload.stagingFile.lastModified().takeIf { it > 0L }
                    ?: upload.registeredAtMillis
            )
        )
    )

    internal fun notifyChanged() {
        mainHandler.post {
            listeners.forEach { listener -> runCatching(listener) }
        }
    }
}

private val Path.normalizedRclonePath: RclonePath?
    get() = (this as? RclonePath)
        ?.toAbsolutePath()
        ?.normalize() as? RclonePath

private val RclonePath.uploadCacheKey: String
    get() = "$remoteName\u0000$remotePath"

private val Path.isInternalTransferTarget: Boolean
    get() = fileName?.toString()?.startsWith(".wizefiles-part-") == true

internal fun planRcloneUploads(entries: Iterable<RcloneUploadPlanEntry>) =
    RcloneUploadCache.plan(entries)

internal fun markRcloneUploadCopying(path: Path) =
    RcloneUploadCache.markPlannedCopying(path)

internal fun moveRcloneUploadPlan(from: Path, to: Path) =
    RcloneUploadCache.movePlan(from, to)

internal fun completeRcloneUploadPlan(path: Path) =
    RcloneUploadCache.completePlan(path)

internal fun removeRcloneUploadPlans(paths: Iterable<Path>) =
    RcloneUploadCache.removePlans(paths)

internal fun rcloneUploadDisplayState(path: Path): RcloneUploadDisplayState? =
    RcloneUploadCache.displayState(path)

/**
 * Process-local tombstones for rclone paths whose remote deletion is still running.
 *
 * The overlay never changes provider reads used by the delete job. It is applied only to browser
 * listings, so recursive scans and the remote operation remain the source of truth.
 */
internal class RcloneDeleteOverlay {
    private val lock = Any()
    private val deletedPaths = linkedMapOf<String, RclonePath>()

    fun stage(paths: Iterable<Path>): Boolean {
        var changed = false
        synchronized(lock) {
            for (path in paths) {
                val rclonePath = path.normalizedRclonePath ?: continue
                if (deletedPaths.put(rclonePath.deleteCacheKey, rclonePath) == null) {
                    changed = true
                }
            }
        }
        return changed
    }

    fun complete(path: Path): Boolean {
        val rclonePath = path.normalizedRclonePath ?: return false
        return synchronized(lock) {
            deletedPaths.remove(rclonePath.deleteCacheKey) != null
        }
    }

    fun rollback(paths: Iterable<Path>): Boolean {
        var changed = false
        synchronized(lock) {
            for (path in paths) {
                val rclonePath = path.normalizedRclonePath ?: continue
                changed = deletedPaths.remove(rclonePath.deleteCacheKey) != null || changed
            }
        }
        return changed
    }

    fun affectsDirectory(directory: RclonePath): Boolean {
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        return synchronized(lock) {
            deletedPaths.values.any { deletedPath ->
                deletedPath.remoteName == directory.remoteName && (
                    normalizedDirectory == deletedPath.parent ||
                        normalizedDirectory == deletedPath ||
                        normalizedDirectory.startsWith(deletedPath)
                    )
            }
        }
    }

    fun filter(entries: List<RcloneListedPath>): List<RcloneListedPath> = synchronized(lock) {
        if (deletedPaths.isEmpty()) {
            return@synchronized entries
        }
        entries.filterNot { entry ->
            val entryPath = entry.path.normalizedRclonePath ?: return@filterNot false
            deletedPaths.values.any { deletedPath ->
                entryPath.remoteName == deletedPath.remoteName &&
                    (entryPath == deletedPath || entryPath.startsWith(deletedPath))
            }
        }
    }

    private val Path.normalizedRclonePath: RclonePath?
        get() = (this as? RclonePath)
            ?.toAbsolutePath()
            ?.normalize() as? RclonePath

    private val RclonePath.deleteCacheKey: String
        get() = "$remoteName\u0000$remotePath"
}

private object RcloneDeleteCache {
    private val overlay = RcloneDeleteOverlay()

    fun stage(paths: Iterable<Path>) {
        if (overlay.stage(paths)) {
            RcloneUploadCache.notifyChanged()
        }
    }

    fun complete(path: Path) {
        if (overlay.complete(path)) {
            RcloneUploadCache.notifyChanged()
        }
    }

    fun rollback(paths: Iterable<Path>) {
        if (overlay.rollback(paths)) {
            RcloneUploadCache.notifyChanged()
        }
    }

    fun affectsDirectory(directory: RclonePath): Boolean =
        overlay.affectsDirectory(directory)

    fun filter(entries: List<RcloneListedPath>): List<RcloneListedPath> =
        overlay.filter(entries)
}

internal fun stageRcloneDeletes(paths: Iterable<Path>) = RcloneDeleteCache.stage(paths)

internal fun rollbackRcloneDeletes(paths: Iterable<Path>) = RcloneDeleteCache.rollback(paths)

internal fun completeRcloneDelete(path: Path) {
    val rclonePath = path as? RclonePath ?: return
    RcloneFileSystemProvider.cacheCompletedDelete(rclonePath)
    RcloneDeleteCache.complete(rclonePath)
}

internal fun hasPendingRcloneDeletes(directory: RclonePath): Boolean =
    RcloneDeleteCache.affectsDirectory(directory)

internal fun filterPendingRcloneDeletes(entries: List<RcloneListedPath>): List<RcloneListedPath> =
    RcloneDeleteCache.filter(entries)

internal fun isRcloneUploadPending(path: Path): Boolean = RcloneUploadCache.isPending(path)

internal fun addRcloneUploadCacheListener(listener: () -> Unit) =
    RcloneUploadCache.addListener(listener)

internal fun removeRcloneUploadCacheListener(listener: () -> Unit) =
    RcloneUploadCache.removeListener(listener)
