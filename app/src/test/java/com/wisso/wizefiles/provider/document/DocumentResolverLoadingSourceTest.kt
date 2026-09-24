package com.wisso.wizefiles.provider.document

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentResolverLoadingSourceTest {
    @Test
    fun `loading providers have a bounded cancellation-safe wait`() {
        val source = projectFile(
            "src/main/java/com/wisso/wizefiles/data/providers/document/resolver/DocumentResolver.kt"
        ) + projectFile(
            "src/main/java/com/wisso/wizefiles/data/providers/document/resolver/DocumentQueryClient.kt"
        )
        assertTrue(source.contains("withTimeoutOrNull(LOADING_TIMEOUT_MILLIS)"))
        assertTrue(source.contains("AtomicBoolean(false)"))
        assertTrue(source.contains("continuation.invokeOnCancellation"))
        assertTrue(source.contains("Timed out while waiting for the document provider"))
    }

    private fun projectFile(path:String):String =
        listOf(File(path),File("app/$path"))
            .firstOrNull(File::exists)
            ?.readText()
            ?: error("Missing $path")
}
