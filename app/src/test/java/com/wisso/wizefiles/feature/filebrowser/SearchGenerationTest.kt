// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class SearchGenerationTest {
    @Test
    fun `only latest search generation can publish`() {
        val generations = SearchGeneration()
        val stale = generations.next()
        val latest = generations.next()

        assertFalse(generations.isCurrent(stale))
        assertTrue(generations.isCurrent(latest))
    }

    @Test
    fun `queued stale result is rejected on delivery executor`() {
        val queued = ArrayDeque<Runnable>()
        val executor = Executor(queued::addLast)
        val generations = SearchGeneration()
        val delivered = mutableListOf<String>()
        val publisher = LatestGenerationPublisher(generations, executor, delivered::add)

        val stale = generations.next()
        publisher.publish(stale, "stale")
        val latest = generations.next()
        publisher.publish(latest, "latest")
        while (queued.isNotEmpty()) queued.removeFirst().run()

        assertTrue(delivered == listOf("latest"))
    }
}
