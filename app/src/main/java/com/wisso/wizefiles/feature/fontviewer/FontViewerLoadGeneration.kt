package com.wisso.wizefiles.feature.fontviewer

/**
 * Identifies the only font load that may publish state or retain a staged session.
 * Superseded and lifecycle-invalidated loads observe a different generation.
 */
internal class FontViewerLoadGeneration {
    private var generation = 0L

    @Synchronized
    fun next(): Long = ++generation

    @Synchronized
    fun invalidate() {
        generation++
    }

    @Synchronized
    fun isCurrent(candidate: Long): Boolean = candidate == generation
}
