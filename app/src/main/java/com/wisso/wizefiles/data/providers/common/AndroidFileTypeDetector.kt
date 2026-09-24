package com.wisso.wizefiles.provider.common

import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.spi.FileTypeDetector
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.forSpecialPosixFileType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import java.io.IOException

object AndroidFileTypeDetector : FileTypeDetector() {
    @Throws(IOException::class)
    override fun probeContentType(path: Path): String {
        val attributes = path.readAttributes(BasicFileAttributes::class.java)
        return getMimeType(path, attributes)
    }

    fun getMimeType(path: Path, attributes: BasicFileAttributes): String {
        MimeType.forSpecialPosixFileType(attributes.posixFileType)?.let { return it.value }
        if (attributes.isDirectory) {
            return MimeType.DIRECTORY.value
        }
        if (attributes is ContentProviderFileAttributes) {
            attributes.mimeType()?.let { return it }
        }
        return MimeType.guessFromPath(path.toString()).value
    }
}
