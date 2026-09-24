// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Random

class NearbyProtocolTest {
    @Test
    fun `offer round trips without exposing source URIs`() {
        val offer = NearbyOffer(
            sessionId = "session-12345678",
            operationId = "operation-1",
            entries = listOf(
                NearbyManifestEntry(
                    id = "item-1",
                    relativePath = "Photos/holiday.jpg",
                    sourceUri = "file:///secret/location/holiday.jpg",
                    directory = false,
                    sizeBytes = 42,
                    modifiedMillis = 123,
                    fingerprint = "f:42:123"
                )
            ),
            totalBytes = 42
        )

        val encoded = NearbyProtocol.offer(offer)
        val decoded = NearbyProtocol.decodeOffer(NearbyProtocol.decode(encoded))

        assertEquals("Photos/holiday.jpg", decoded.entries.single().relativePath)
        assertEquals("", decoded.entries.single().sourceUri)
        assertTrue(!String(encoded).contains("secret/location"))
    }

    @Test
    fun `large manifests are compressed within the bytes payload limit`() {
        val entries = (0 until 12_000).map { index ->
            NearbyManifestEntry(
                id = "item-$index",
                relativePath = "folder-${index / 100}/repeated-file-name-$index.txt",
                directory = false,
                sizeBytes = 1,
                modifiedMillis = 100,
                fingerprint = "f:1:100"
            )
        }
        val encoded = NearbyProtocol.offer(
            NearbyOffer("session-12345678", "operation-large", entries, entries.size.toLong())
        )

        assertTrue(encoded.size <= 1_000_000)
        assertEquals(entries.size, NearbyProtocol.decodeOffer(NearbyProtocol.decode(encoded)).entries.size)
    }

    @Test
    fun `unsafe relative paths are rejected`() {
        listOf(
            "../escape.txt",
            "/absolute.txt",
            "C:/windows.txt",
            "folder\\escape.txt",
            "folder//empty.txt",
            "folder/./dot.txt",
            "folder/../up.txt",
            "folder/\u0000bad.txt"
        ).forEach { path ->
            assertTrue(
                "Expected rejection for $path",
                runCatching { NearbyPathSecurity.requireSafeRelativePath(path) }.isFailure
            )
        }
    }

    @Test
    fun `offers reject duplicate item ids and destination paths`() {
        val first = NearbyManifestEntry(
            id = "item-1",
            relativePath = "Folder/first.txt",
            directory = false,
            sizeBytes = 1,
            modifiedMillis = 1,
            fingerprint = "f:1:1"
        )
        assertTrue(
            runCatching {
                NearbyOffer("session", "operation", listOf(first, first.copy(relativePath = "Folder/second.txt")), 2)
            }.isFailure
        )
        assertTrue(
            runCatching {
                NearbyOffer("session", "operation", listOf(first, first.copy(id = "item-2")), 2)
            }.isFailure
        )
    }

    @Test
    fun `resolved destination remains inside accepted root`() {
        val root = java.nio.file.Paths.get("/tmp/received")
        assertEquals(
            root.resolve("Folder/file.txt"),
            NearbyPathSecurity.resolveInside(root, "Folder/file.txt")
        )
    }

    @Test
    fun `destination rejects an existing symbolic link ancestor`() {
        val root = Files.createTempDirectory("nearby-root")
        val outside = Files.createTempDirectory("nearby-outside")
        val link = root.resolve("linked")
        try {
            assumeTrue(runCatching { Files.createSymbolicLink(link, outside) }.isSuccess)
            assertTrue(
                runCatching {
                    NearbyPathSecurity.resolveInside(root, "linked/escaped.txt")
                }.isFailure
            )
        } finally {
            Files.deleteIfExists(link)
            Files.deleteIfExists(root)
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `bounded compressed message fuzz never leaks parser implementation failures`() {
        val random = Random(0x4E454152L)
        repeat(10_000) {
            val bytes = ByteArray(random.nextInt(2048)).also(random::nextBytes)
            val failure = runCatching { NearbyProtocol.decode(bytes) }.exceptionOrNull()
            assertTrue(
                failure == null || failure is IllegalArgumentException ||
                    failure is java.io.IOException || failure is org.json.JSONException
            )
        }
    }
}
