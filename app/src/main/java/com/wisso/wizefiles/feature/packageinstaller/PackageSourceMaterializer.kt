package com.wisso.wizefiles.feature.packageinstaller

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException

internal class PackageSourceMaterializer(private val contentResolver: ContentResolver) {
    fun materialize(source: Uri, target: File) {
        contentResolver.openInputStream(source)?.buffered().use { input ->
            if (input == null) throw IOException("Unable to open package source")
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total = Math.addExact(total, count.toLong())
                    if (total > MAXIMUM_SOURCE_BYTES) {
                        throw IOException("Package source exceeds the supported size")
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        if (target.length() == 0L) throw IOException("Package source is empty")
    }

    private companion object {
        const val MAXIMUM_SOURCE_BYTES = 8L * 1024L * 1024L * 1024L
    }
}
