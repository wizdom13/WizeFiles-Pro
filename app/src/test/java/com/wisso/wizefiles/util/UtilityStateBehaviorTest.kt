package com.wisso.wizefiles.util

import com.wisso.wizefiles.provider.common.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityStateBehaviorTest {

    @Test
    fun actionStatesExposeTheirLifecyclePhase() {
        val ready: ActionState<String, Int> = ActionState.Ready()
        val running: ActionState<String, Int> = ActionState.Running("input")
        val success: ActionState<String, Int> = ActionState.Success("input", 7)
        val error: ActionState<String, Int> = ActionState.Error("input", IllegalStateException())

        assertTrue(ready.isReady)
        assertEquals(ActionState.Ready<String, Int>(), ready)
        assertTrue(running.isRunning)
        assertFalse(running.isFinished)
        assertTrue(success.isFinished)
        assertTrue(error.isFinished)
    }

    @Test
    fun dataStateConversionsPreserveCachedData() {
        val success: DataState<String> = DataState.Success("cached")
        val loading = success.toLoading()
        val failure = loading.toError(IllegalArgumentException("failed"))

        assertEquals("cached", loading.data)
        assertEquals("cached", failure.data)
        assertTrue(loading === loading.toLoading())
    }

    @Test
    fun statefulVariantsExposeCurrentValue() {
        assertNull(Loading<String>(null).value)
        assertEquals("old", Failure("old", IllegalStateException()).value)
        assertEquals("new", Success("new").value)
    }

    @Test
    fun mapSetUsesKeysForMembershipAndReplacesEqualKeyValues() {
        data class Item(val id: Int, val label: String)

        val items = MapSet<Int, Item>(Item::id)
        assertTrue(items.add(Item(1, "first")))
        assertFalse(items.add(Item(1, "replacement")))
        assertEquals(listOf(Item(1, "replacement")), items.toList())
        assertTrue(items.contains(Item(1, "different")))
        assertTrue(items.remove(Item(1, "ignored")))
        assertTrue(items.isEmpty())
    }

    @Test
    fun linkedMapSetKeepsInsertionOrderAcrossReplacements() {
        data class Item(val id: Int, val label: String)

        val items = LinkedMapSet<Int, Item>(Item::id)
        items.add(Item(2, "second"))
        items.add(Item(1, "first"))
        items.add(Item(2, "updated"))

        assertEquals(listOf(2, 1), items.map(Item::id))
        assertEquals("updated", items.first().label)
    }

    @Test
    fun pathNamesSeparateLeafParentAndCompoundExtensions() {
        val path = "/archive/backups/report.tar.gz".asPathName()
        val file = path.fileName!!.asFileName()

        assertEquals("report.tar.gz", path.fileName)
        assertEquals("/archive/backups", path.directoryName)
        assertEquals("gz", file.singleExtension)
        assertEquals("tar.gz", file.extensions)
        assertEquals("report", file.baseName)
        assertNull("/archive/backups/".asPathName().fileName)
        assertEquals("/archive/backups", "/archive/backups/".asPathName().directoryName)
    }

    @Test
    fun invalidPathAndFileNamesAreRejected() {
        assertNull("".asPathNameOrNull())
        assertNull("bad\u0000path".asPathNameOrNull())
        assertNull("folder/file".asFileNameOrNull())
    }

    @Test
    fun bytePathNamesMatchStringPathSemantics() {
        val path = ByteString.fromString("/archive/report.tar.xz").asPathName()
        val file = path.fileName!!.asFileName()

        assertEquals("report.tar.xz", path.fileName.toString())
        assertEquals("/archive", path.directoryName.toString())
        assertEquals("tar.xz", file.extensions.toString())
        assertEquals("report", file.baseName.toString())
    }

    @Test
    fun digestAndHexFormattingAreStable() {
        val bytes = "abc".toByteArray()

        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", bytes.sha1Digest().toHexString())
        assertEquals("00FF10", byteArrayOf(0, -1, 16).toHexString())
        assertArrayEquals(bytes.sha1Digest(), bytes.sha1Digest())
    }
}
