package com.wisso.wizefiles.storage.legacy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class LegacyRetrofileAdapterResilienceTest {

    @Test
    fun `one stale or inaccessible entry does not fail the directory listing`() {
        val candidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/storage/legacy/LegacyRetrofileAdapter.kt"),
            Paths.get("src/main/java/com/wisso/wizefiles/storage/legacy/LegacyRetrofileAdapter.kt")
        )
        val file = candidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate LegacyRetrofileAdapter.kt")
        val source = String(Files.readAllBytes(file), StandardCharsets.UTF_8)

        assertTrue(source.contains("mapNotNull(::loadEntryOrNull)"))
        assertTrue(source.contains("catch (_: java.nio.file.NoSuchFileException)"))
        assertTrue(source.contains("catch (_: java.io.FileNotFoundException)"))
        assertTrue(source.contains("catch (_: IOException)"))
        assertTrue(source.contains("catch (_: SecurityException)"))
    }
}
