package com.wisso.wizefiles.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readLines
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CheckedInHostileCorpusTest {
    private val corpusRoot: Path = Path("..", "fuzz-corpus").toAbsolutePath().normalize()

    @Test fun `every checked in archive path seed is rejected`() {
        val seeds = corpusRoot.resolve("archive/paths.txt").readLines().filter(String::isNotEmpty)
        assertTrue(seeds.isNotEmpty())
        seeds.forEach { seed ->
            assertThrows(seed, IllegalArgumentException::class.java) { SecureRelativePath.validate(seed) }
        }
    }

    @Test fun `every checked in vault frame seed has a classified rejection`() {
        val seeds = corpusRoot.resolve("vault/frames.hex").readLines()
        assertTrue(seeds.isNotEmpty())
        seeds.forEach { seed ->
            val bytes = seed.hexToBytes()
            assertThrows(seed, FrameValidationException::class.java) { VaultPayloadFrame.decode(bytes) }
        }
    }

    @Test fun `all corpus files are bounded regular files`() {
        Files.walk(corpusRoot).use { paths ->
            val files = paths.filter(Files::isRegularFile).toList()
            assertTrue(files.isNotEmpty())
            files.forEach { file -> assertTrue("$file exceeds corpus budget", Files.size(file) <= 1_000_000) }
        }
    }

    @Test fun `mutated vault and archive corpus never escapes classified parser outcomes`() {
        val random = Random(0x5712E)
        corpusRoot.resolve("vault/frames.hex").readLines().filter(String::isNotEmpty).forEach { seed ->
            repeat(256) {
                val mutated = seed.hexToBytes().mutate(random)
                try {
                    val decoded = VaultPayloadFrame.decode(mutated)
                    assertTrue(decoded.size <= VaultPayloadFrame.MAX_PAYLOAD_BYTES)
                    assertTrue(VaultPayloadFrame.decode(VaultPayloadFrame.encode(decoded)).contentEquals(decoded))
                } catch (_: FrameValidationException) {
                    // Expected classified rejection.
                }
            }
        }
        corpusRoot.resolve("archive/paths.txt").readLines().filter(String::isNotEmpty).forEach { seed ->
            repeat(256) {
                val candidate = seed.toByteArray().mutate(random).toString(Charsets.UTF_8)
                try {
                    val accepted = SecureRelativePath.validate(candidate)
                    assertTrue(accepted.isNotEmpty() && !accepted.startsWith('/') && ".." !in accepted.split('/'))
                } catch (_: IllegalArgumentException) {
                    // Expected classified rejection.
                }
            }
        }
    }

    private fun ByteArray.mutate(random: Random): ByteArray {
        val result = if (isEmpty()) byteArrayOf(0) else copyOf()
        repeat(1 + random.nextInt(4)) {
            val index = random.nextInt(result.size)
            result[index] = (result[index].toInt() xor (1 shl random.nextInt(8))).toByte()
        }
        return result
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "Invalid hex seed" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
