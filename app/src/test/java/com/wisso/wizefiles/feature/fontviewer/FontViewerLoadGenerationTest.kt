package com.wisso.wizefiles.feature.fontviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontViewerLoadGenerationTest {
    @Test
    fun `new load supersedes the previous generation`() {
        val generations = FontViewerLoadGeneration()
        val first = generations.next()
        val second = generations.next()

        assertFalse(generations.isCurrent(first))
        assertTrue(generations.isCurrent(second))
    }

    @Test
    fun `lifecycle invalidation rejects the active load`() {
        val generations = FontViewerLoadGeneration()
        val active = generations.next()

        generations.invalidate()

        assertFalse(generations.isCurrent(active))
    }
}
