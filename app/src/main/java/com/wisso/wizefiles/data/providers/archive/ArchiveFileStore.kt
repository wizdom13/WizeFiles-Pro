package com.wisso.wizefiles.provider.archive

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.provider.archive.legacy.archivePathString
import com.wisso.wizefiles.provider.archive.legacy.requireLegacyArchivePath
import com.wisso.wizefiles.provider.archive.legacy.toArchiveAppPath
import com.wisso.wizefiles.provider.common.PosixFileStore
import com.wisso.wizefiles.provider.common.size
import com.wisso.wizefiles.storage.path.AppPath
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.PosixFileAttributeView

internal class ArchiveFileStore(private val archiveFile: AppPath) : PosixFileStore() {
    constructor(archiveFile: Path) : this(archiveFile.toArchiveAppPath())

    override fun refresh() {}

    override fun name(): String = archiveFile.archivePathString()

    override fun type(): String = MimeType.guessFromPath(archiveFile.archivePathString()).value

    override fun isReadOnly(): Boolean = true

    @Throws(IOException::class)
    override fun setReadOnly(readOnly: Boolean) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getTotalSpace(): Long = archiveFile.requireLegacyArchivePath().size()

    override fun getUsableSpace(): Long = 0

    override fun getUnallocatedSpace(): Long = 0

    override fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        type == BasicFileAttributeView::class.java
            || type == PosixFileAttributeView::class.java

    override fun supportsFileAttributeView(name: String): Boolean =
        name in ArchiveFileAttributeView.SUPPORTED_NAMES
}
