package com.wisso.wizefiles.provider.rclone

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneUploadCacheTest {
    @Test
    fun `pending files overlay stale remote entries without hiding siblings`() {
        val directory = createRcloneRootPath("test").resolve("folder")
        val oldFile = directory.resolve("upload.txt")
        val sibling = directory.resolve("existing.txt")
        val remote = listOf(
            listed(oldFile, 10),
            listed(sibling, 20)
        )
        val pending = listOf(listed(oldFile, 30))

        val merged = mergeRcloneListedPaths(remote, pending)

        assertEquals(2, merged.size)
        assertEquals(30L, merged.single { it.path == oldFile }.attributes.size())
        assertTrue(merged.any { it.path == sibling })
    }

    @Test
    fun `pending files are usable before a remote listing has been cached`() {
        val pendingFile = createRcloneRootPath("test").resolve("new.txt")

        val merged = mergeRcloneListedPaths(emptyList(), listOf(listed(pendingFile, 42)))

        assertEquals(listOf(pendingFile), merged.map(RcloneListedPath::path))
        assertEquals(42L, merged.single().attributes.size())
    }

    @Test
    fun `pending delete hides only the selected cached item`() {
        val directory = createRcloneRootPath("test").resolve("folder")
        val deleted = directory.resolve("deleted.txt")
        val sibling = directory.resolve("existing.txt")
        val overlay = RcloneDeleteOverlay()

        overlay.stage(listOf(deleted))
        val visible = overlay.filter(listOf(listed(deleted, 10), listed(sibling, 20)))

        assertTrue(overlay.affectsDirectory(directory as RclonePath))
        assertEquals(listOf(sibling), visible.map(RcloneListedPath::path))
    }

    @Test
    fun `pending folder delete hides the folder and its descendants`() {
        val root = createRcloneRootPath("test")
        val folder = root.resolve("folder")
        val child = folder.resolve("child.txt")
        val overlay = RcloneDeleteOverlay()

        overlay.stage(listOf(folder))

        assertTrue(overlay.filter(listOf(listed(folder, 0))).isEmpty())
        assertTrue(overlay.filter(listOf(listed(child, 10))).isEmpty())
        assertTrue(overlay.affectsDirectory(folder as RclonePath))
    }

    @Test
    fun `completed delete clears its tombstone`() {
        val file = createRcloneRootPath("test").resolve("completed.txt")
        val entry = listed(file, 10)
        val overlay = RcloneDeleteOverlay()

        overlay.stage(listOf(file))
        assertTrue(overlay.complete(file))

        assertEquals(listOf(file), overlay.filter(listOf(entry)).map(RcloneListedPath::path))
    }

    @Test
    fun `rollback restores a failed or cancelled delete`() {
        val file = createRcloneRootPath("test").resolve("restore.txt")
        val entry = listed(file, 10)
        val overlay = RcloneDeleteOverlay()

        overlay.stage(listOf(file))
        assertTrue(overlay.filter(listOf(entry)).isEmpty())

        overlay.rollback(listOf(file))

        assertEquals(listOf(file), overlay.filter(listOf(entry)).map(RcloneListedPath::path))
    }

    @Test
    fun `batch plans expose every destination as queued before staging`() {
        val directory = createRcloneRootPath("test").resolve("folder")
        val first = directory.resolve("first.txt")
        val second = directory.resolve("second.txt")
        val plans = RcloneUploadPlanOverlay()

        plans.plan(
            listOf(
                RcloneUploadPlanEntry(first, listed(first, 10).attributes),
                RcloneUploadPlanEntry(second, listed(second, 20).attributes)
            )
        )

        assertEquals(
            listOf(first, second),
            plans.pendingChildren(directory as RclonePath).map(RcloneListedPath::path)
        )
        assertEquals(RcloneUploadDisplayState.QUEUED, plans.state(first))
        assertEquals(RcloneUploadDisplayState.QUEUED, plans.state(second))
    }

    @Test
    fun `planned destination stays display-only until it exists remotely`() {
        val directory = createRcloneRootPath("test").resolve("folder")
        val target = directory.resolve("photo.jpg")
        val plans = RcloneUploadPlanOverlay()
        plans.plan(listOf(RcloneUploadPlanEntry(target, listed(target, 10).attributes)))

        assertEquals(
            listOf(target),
            plans.pendingChildren(directory as RclonePath).map(RcloneListedPath::path)
        )
        assertFalse(rclonePathExistsRemotely(target as RclonePath) { _, _ -> null })
        assertTrue(
            rclonePathExistsRemotely(target) { _, remotePath ->
                RcloneEntry(remotePath, "photo.jpg", 10L, Instant.EPOCH, false)
            }
        )
    }

    @Test
    fun `active item changes from queued to copying without removing siblings`() {
        val directory = createRcloneRootPath("test").resolve("folder")
        val first = directory.resolve("first.txt")
        val second = directory.resolve("second.txt")
        val plans = RcloneUploadPlanOverlay()
        plans.plan(
            listOf(
                RcloneUploadPlanEntry(first, listed(first, 10).attributes),
                RcloneUploadPlanEntry(second, listed(second, 20).attributes)
            )
        )

        assertTrue(plans.markCopying(first))

        assertEquals(RcloneUploadDisplayState.COPYING, plans.state(first))
        assertEquals(RcloneUploadDisplayState.QUEUED, plans.state(second))
        assertEquals(2, plans.pendingChildren(directory as RclonePath).size)
    }

    @Test
    fun `renamed conflict moves the placeholder and completes through its original target`() {
        val directory = createRcloneRootPath("test").resolve("folder")
        val original = directory.resolve("photo.jpg")
        val renamed = directory.resolve("photo (1).jpg")
        val plans = RcloneUploadPlanOverlay()
        plans.plan(listOf(RcloneUploadPlanEntry(original, listed(original, 10).attributes)))
        plans.markCopying(original)

        assertTrue(plans.move(original, renamed))
        val completed = plans.complete(original)

        assertEquals(renamed, completed?.path)
        assertEquals(10L, completed?.attributes?.size())
        assertTrue(plans.pendingChildren(directory as RclonePath).isEmpty())
    }

    @Test
    fun `completed skipped and cancelled plans are removable in one batch`() {
        val directory = createRcloneRootPath("test").resolve("folder")
        val first = directory.resolve("first.txt")
        val second = directory.resolve("second.txt")
        val plans = RcloneUploadPlanOverlay()
        plans.plan(
            listOf(
                RcloneUploadPlanEntry(first, listed(first, 10).attributes),
                RcloneUploadPlanEntry(second, listed(second, 20).attributes)
            )
        )

        assertTrue(plans.remove(listOf(first, second)))

        assertTrue(plans.pendingChildren(directory as RclonePath).isEmpty())
        assertFalse(plans.markCopying(first))
    }

    private fun listed(path: java.nio.file.Path, size: Long) = RcloneListedPath(
        path,
        RcloneFileAttributes(path.toString(), false, size, Instant.EPOCH)
    )
}
