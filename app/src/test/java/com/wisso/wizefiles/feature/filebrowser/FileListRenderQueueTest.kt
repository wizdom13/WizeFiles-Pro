package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListRenderQueueTest {
    @Test
    fun `latest replacement snapshot wins before the render gate opens`() {
        val queue = FileListRenderQueue()

        queue.replace(emptyList(), isSearching = false)
        queue.replace(emptyList(), isSearching = true)

        val update = queue.take()
        assertTrue(update is FileListRenderQueue.ListUpdate.Replace)
        assertTrue((update as FileListRenderQueue.ListUpdate.Replace).isSearching)
        assertSame(FileListRenderQueue.ListUpdate.None, queue.take())
    }

    @Test
    fun `clear supersedes an unrendered replacement`() {
        val queue = FileListRenderQueue()

        queue.replace(emptyList(), isSearching = true)
        queue.clear()

        assertSame(FileListRenderQueue.ListUpdate.Clear, queue.take())
        assertSame(FileListRenderQueue.ListUpdate.None, queue.take())
    }

    @Test
    fun `reset discards pending work during view teardown`() {
        val queue = FileListRenderQueue()
        queue.replace(emptyList(), isSearching = true)

        queue.reset()

        val update = queue.take()
        assertSame(FileListRenderQueue.ListUpdate.None, update)
        assertFalse(update is FileListRenderQueue.ListUpdate.Replace)
    }
}
