package com.wisso.wizefiles.core.android.compat

import android.net.Uri
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidCompatFoundationRobolectricTest {
    @Test
    fun threadLocalFactoryRunsOnceForEachThread() {
        var creations = 0
        val local = ThreadLocal::class.withInitial { ++creations }

        assertEquals(1, local.get())
        assertEquals(1, local.get())
        val workerValue = arrayOfNulls<Int>(1)
        Thread { workerValue[0] = local.get() }.apply { start(); join() }
        assertEquals(2, workerValue[0])
        assertEquals(2, creations)
    }

    @Test
    fun nullInputStreamValidatesRangesAndRejectsReadsAfterClose() {
        val input = InputStream::class.nullInputStream()
        assertEquals(-1, input.read())
        assertEquals(0, input.read(ByteArray(2), 1, 0))
        assertThrows(IndexOutOfBoundsException::class.java) {
            input.read(ByteArray(2), 2, 1)
        }

        input.close()
        assertThrows(IOException::class.java) { input.available() }
    }

    @Test
    fun documentUriShapesAreRecognizedWithoutProviderCalls() {
        val document = Uri.parse("content://authority/document/id")
        val treeDocument = Uri.parse("content://authority/tree/root/document/id")
        val children = Uri.parse("content://authority/tree/root/document/id/children")
        val unrelated = Uri.parse("content://authority/folder/id")

        assertTrue(DocumentsContractCompat.isDocumentUri(document))
        assertTrue(DocumentsContractCompat.isDocumentUri(treeDocument))
        assertTrue(DocumentsContractCompat.isChildDocumentsUri(children))
        assertFalse(DocumentsContractCompat.isDocumentUri(unrelated))
    }
}
