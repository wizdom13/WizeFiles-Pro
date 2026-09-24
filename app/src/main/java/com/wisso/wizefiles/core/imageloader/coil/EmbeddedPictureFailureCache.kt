package com.wisso.wizefiles.core.imageloader.coil

import com.wisso.wizefiles.storage.path.AppPath
import java.util.Collections

internal object EmbeddedPictureFailureCache {
    private val failedKeys = Collections.synchronizedSet(mutableSetOf<String>())

    fun shouldSkip(path: AppPath): Boolean = failedKeys.contains(path.toString())

    fun markFailed(path: AppPath) {
        failedKeys += path.toString()
    }

    fun clear(path: AppPath) {
        failedKeys -= path.toString()
    }
}
