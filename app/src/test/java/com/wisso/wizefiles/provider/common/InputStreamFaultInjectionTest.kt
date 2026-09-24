package com.wisso.wizefiles.provider.common

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InputStreamFaultInjectionTest {
    @Test
    fun `disk full failure is propagated without reporting completion`() {
        val progress = mutableListOf<Long>()
        val diskFull = object : OutputStream() {
            override fun write(value: Int) = throw IOException("ENOSPC")
            override fun write(buffer: ByteArray, offset: Int, length: Int) =
                throw IOException("ENOSPC")
        }

        val failure = assertThrows(IOException::class.java) {
            ByteArrayInputStream(ByteArray(16 * 1024)).copyTo(diskFull, 0, progress::add)
        }

        assertEquals("ENOSPC", failure.message)
        assertEquals(emptyList<Long>(), progress)
    }

    @Test
    fun `thread interruption aborts after the current bounded chunk`() {
        val output = CountingOutputStream()
        Thread.currentThread().interrupt()

        assertThrows(InterruptedIOException::class.java) {
            ByteArrayInputStream(ByteArray(32 * 1024)).copyTo(output, Long.MAX_VALUE, null)
        }

        assertEquals(DEFAULT_BUFFER_SIZE.toLong(), output.bytesWritten)
    }

    private class CountingOutputStream : OutputStream() {
        var bytesWritten = 0L
        override fun write(value: Int) { bytesWritten++ }
        override fun write(buffer: ByteArray, offset: Int, length: Int) { bytesWritten += length }
    }
}
