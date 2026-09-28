// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class SyncthingControlTest {
    private fun response(text: String) = SyncthingHttp.read(text.byteInputStream())

    @Test fun `reads fixed and chunked UTF8 responses`() {
        assertEquals("abc", response("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc"))
        assertEquals("abcde", response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n"))
    }

    @Test fun `rejects truncated oversized and ambiguous response bodies`() {
        listOf(
            "Content-Length: 4\r\n\r\nabc",
            "Content-Length: 2147483647\r\n\r\n",
            "Content-Length: 0\r\nContent-Length: 1\r\n\r\n",
            "Transfer-Encoding: chunked\r\nContent-Length: 0\r\n\r\n0\r\n",
            "Transfer-Encoding: gzip\r\n\r\n"
        ).forEach { body -> assertThrows(IOException::class.java) { response("HTTP/1.1 200 OK\r\n$body") } }
    }

    @Test fun `does not expose response bodies in errors`() {
        val failure = assertThrows(IOException::class.java) {
            response("HTTP/1.1 403 Forbidden\r\nContent-Length: 6\r\n\r\nsecret")
        }
        assertFalse(failure.message.orEmpty().contains("secret"))
    }

    @Test fun `request encoding prevents header injection and measures UTF8 bytes`() {
        val output = ByteArrayOutputStream()
        SyncthingHttp.write(output, "PUT", "/rest/config", "a".repeat(64), "é")
        assertTrue(output.toString("UTF-8").contains("Content-Length: 2\r\n"))
        assertEquals("folder%2Fa%20b", SyncthingHttp.component("folder/a b"))
        assertThrows(IllegalArgumentException::class.java) {
            SyncthingHttp.write(output, "GET", "/rest/x\r\nInjected: yes", "a".repeat(64), null)
        }
    }

    @Test fun `empty requests tolerate peer closing as soon as headers arrive`() {
        for (method in listOf("GET", "POST", "DELETE")) {
            for (body in listOf(null, "")) {
                val output = object : ByteArrayOutputStream() {
                    private var peerClosed = false
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        if (peerClosed) throw IOException("Broken pipe")
                        super.write(bytes, offset, length)
                        // Model LocalSocket's native send even when length == 0. A peer
                        // can close once it receives a complete Content-Length: 0 request.
                        peerClosed = toString("US-ASCII").endsWith("\r\n\r\n")
                    }
                }
                SyncthingHttp.write(output, method, "/rest/system/status", "a".repeat(64), body)
                assertTrue(output.toString("US-ASCII").endsWith("Content-Length: 0\r\n\r\n"))
            }
        }
    }

    @Test fun `ignores preserve user rules and replace managed exclusions`() {
        val user = listOf("!important.txt", "#include custom.rules", "*.bak")
        val first = SyncthingIgnorePolicy.merge(user, listOf("*.tmp"))
        val updated = SyncthingIgnorePolicy.merge(first, listOf("*.log"))
        assertEquals(user, updated.take(user.size))
        assertFalse(updated.contains("*.tmp"))
        assertEquals(updated, SyncthingIgnorePolicy.merge(updated, listOf("*.log")))
        assertThrows(IllegalArgumentException::class.java) {
            SyncthingIgnorePolicy.merge(listOf("// WizeFiles managed rules begin"), emptyList())
        }
    }

    @Test fun `bootstrap is offline before first engine startup`() {
        val xml = "<configuration><options><listenAddress>default</listenAddress>" +
            "<globalAnnounceEnabled>true</globalAnnounceEnabled></options></configuration>"
        val safe = SyncthingBootstrap.offline(xml)
        assertTrue(safe.contains("<globalAnnounceEnabled>false</globalAnnounceEnabled>"))
        assertTrue(safe.contains("<listenAddress>tcp://127.0.0.1:0</listenAddress>"))
        assertTrue(safe.contains("<relaysEnabled>false</relaysEnabled>"))
        assertFalse(safe.contains(">default<"))
        assertThrows(IllegalArgumentException::class.java) {
            SyncthingBootstrap.offline("<!DOCTYPE config SYSTEM 'file:///private'>" + xml)
        }
    }

    @Test fun `completion requires stable connected peer acknowledgement`() {
        val policy = SyncthingCompletionPolicy()
        val idle = SyncthingFolderStatus(SyncthingFolderState.IDLE)
        assertFalse(policy.observe(idle))
        assertFalse(policy.observe(idle))
        assertFalse(policy.observe(SyncthingFolderStatus(SyncthingFolderState.DISCONNECTED)))
        assertFalse(policy.observe(idle))
        assertFalse(policy.observe(idle))
        assertTrue(policy.observe(idle))
        assertFalse(policy.observe(idle.copy(pendingItems = 1)))
    }
}
