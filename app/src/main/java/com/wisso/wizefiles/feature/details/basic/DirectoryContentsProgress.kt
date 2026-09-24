package com.wisso.wizefiles.feature.details.basic

internal data class DirectoryContentsSnapshot(val count: Int, val size: Long)

internal class DirectoryContentsProgress(
    private val intervalMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var count = 0
    private var size = 0L
    private var lastPublishedAt = nowMillis()

    fun record(includePath: Boolean, byteSize: Long?): DirectoryContentsSnapshot? {
        if (includePath) count += 1
        if (byteSize != null) size += byteSize
        val now = nowMillis()
        if (now < lastPublishedAt + intervalMillis) return null
        lastPublishedAt = now
        return snapshot()
    }

    fun snapshot(): DirectoryContentsSnapshot = DirectoryContentsSnapshot(count, size)
}
