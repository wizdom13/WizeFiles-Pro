package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListRecyclerSafetySourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `list snapshots are coalesced until RecyclerView is safe`() {
        val coordinator = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListRenderCoordinator.kt"
        )

        assertTrue("pending = ListUpdate.Replace(files.toList(), isSearching)" in coordinator)
        assertTrue(
            "recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE" in coordinator
        )
        assertTrue("recyclerView.isComputingLayout" in coordinator)
        assertTrue("recyclerView.postOnAnimation(flushRunnable)" in coordinator)
        assertTrue("listUpdates.replace(files, isSearching)" in coordinator)
        assertTrue(
            "adapter.replaceListAndIsSearching(listUpdate.files, listUpdate.isSearching)" in
                coordinator
        )
    }

    @Test
    fun `resets and teardown finish RecyclerView work safely`() {
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val coordinator = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListRenderCoordinator.kt"
        )
        val adapter = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt"
        )

        assertTrue("supportsChangeAnimations = false" in fragment)
        assertTrue("view.stopScroll()" in coordinator)
        assertTrue("view.itemAnimator?.endAnimations()" in coordinator)
        assertTrue("removeOnScrollListener(scrollListener)" in coordinator)
        assertTrue("removeCallbacks(flushRunnable)" in coordinator)
        assertTrue("view.adapter = null" in coordinator)
        assertFalse("RecyclerItemAnimationHelper.applyFadeIn" in adapter)
    }

    @Test
    fun `selection and presentation payloads use the same update gate`() {
        val coordinator = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListRenderCoordinator.kt"
        )

        assertTrue("pendingSelectedFiles = FileItemSet().apply { addAll(files) }" in coordinator)
        assertTrue("adapter.replaceSelectedFiles(it)" in coordinator)
        assertTrue("hasPendingPickOptions = true" in coordinator)
        assertTrue("pendingNameEllipsize = nameEllipsize" in coordinator)
    }

    @Test
    fun `successful file operations invalidate active directory listings`() {
        val liveData = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListLiveData.kt"
        )
        val service = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt"
        )
        val createJob = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/CreateFileOperationJob.kt"
        )

        assertTrue("FileOperationService.addFileListRefreshListener(refreshListener)" in liveData)
        assertTrue("FileOperationService.removeFileListRefreshListener(refreshListener)" in liveData)
        assertTrue("listeners.forEach { it() }" in service)
        assertTrue("create(path, createDirectory)" in createJob)
        assertTrue("notifyFileListRefresh()" in createJob)
    }

    private fun source(path: String): String = File(root, path).readText()
}
