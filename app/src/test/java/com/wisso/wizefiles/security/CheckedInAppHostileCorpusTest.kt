// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.security

import com.wisso.wizefiles.feature.nearby.NearbyProtocol
import com.wisso.wizefiles.provider.document.resolver.DocumentQueryPolicy
import java.nio.file.Paths
import kotlin.io.path.readLines
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CheckedInAppHostileCorpusTest {
    private val root = Paths.get("..", "fuzz-corpus").toAbsolutePath().normalize()

    @Test fun `every nearby control seed is rejected within the parser budget`() {
        val seeds = root.resolve("nearby/control.hex").readLines().filter(String::isNotEmpty)
        assertTrue(seeds.isNotEmpty())
        seeds.forEach { seed ->
            assertThrows(seed, Exception::class.java) { NearbyProtocol.decode(seed.hexToBytes()) }
        }
    }

    @Test fun `native seeds are valid bounded replay inputs`() {
        val seeds = root.resolve("native/containers.hex").readLines().filter(String::isNotEmpty)
        assertTrue(seeds.isNotEmpty())
        seeds.forEach { seed -> assertTrue(seed.hexToBytes().size <= 1_000_000) }
    }

    @Test fun `every document metadata seed reaches a bounded decision`() {
        val seeds = root.resolve("document/bundles.txt").readLines().filter(String::isNotEmpty)
        assertTrue(seeds.isNotEmpty())
        seeds.forEach { seed ->
            val fields = seed.split(';').associate { field -> field.substringBefore('=') to field.substringAfter('=') }
            val decision = DocumentQueryPolicy.decide(
                fields.getValue("loading").toBooleanStrict(),
                fields["error"],
                fields.getValue("refresh").toInt()
            )
            if (decision is DocumentQueryPolicy.Decision.Fail) {
                assertTrue(decision.message.length <= DocumentQueryPolicy.MAX_PROVIDER_MESSAGE_LENGTH)
            }
        }
    }

    @Test fun `mutated provider diagnostics and refresh counts stay bounded`() {
        val random = Random(0xD0C0)
        repeat(2_000) {
            val length = random.nextInt(0, 20_000)
            val diagnostic = buildString(length) {
                repeat(length) { append(random.nextInt(0x20, 0x7f).toChar()) }
            }
            val decision = DocumentQueryPolicy.decide(
                loading = random.nextBoolean(),
                error = diagnostic,
                refreshCount = random.nextInt(-100, 10_000)
            )
            if (decision is DocumentQueryPolicy.Decision.Fail) {
                assertTrue(decision.message.length <= DocumentQueryPolicy.MAX_PROVIDER_MESSAGE_LENGTH)
            }
        }
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
