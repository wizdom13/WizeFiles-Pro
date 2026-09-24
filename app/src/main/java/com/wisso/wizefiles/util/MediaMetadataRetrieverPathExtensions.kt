package com.wisso.wizefiles.util

import android.media.MediaMetadataRetriever
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath

val AppPath.isMediaMetadataRetrieverCompatible: Boolean
    get() = this is LocalAppPath

fun MediaMetadataRetriever.setDataSource(path: AppPath) {
    when (path) {
        is LocalAppPath -> setDataSource(path.file.path)
        else -> throw IllegalArgumentException(path.rawPath)
    }
}

internal inline fun <T> runMediaProbeOrNull(
    setDataSource: () -> Unit,
    readValue: () -> T?
): T? {
    return try {
        setDataSource()
        try {
            readValue()
        } catch (_: RuntimeException) {
            null
        }
    } catch (_: RuntimeException) {
        null
    }
}
