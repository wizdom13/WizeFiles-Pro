package com.wisso.wizefiles.provider.archive.archiver

import android.system.OsConstants
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.time.Instant
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixFileType
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.libarchive.Archive
import com.wisso.libarchive.ArchiveEntry
import com.wisso.libarchive.ArchiveException

class ReadArchive : Closeable {
    private val archive = Archive.readNew()
    private fun setCharsetBestEffort() {
        try {
            Archive.setCharset(archive, StandardCharsets.UTF_8.name().toByteArray())
        } catch (e: ArchiveException) {
            if (e.message != "archive_set_option(hdrcharset) symbol is unavailable") {
                throw e
            }
        }
    }

    @Throws(ArchiveException::class)
    constructor(inputStream: InputStream, passwords: List<String>) {
        var successful = false
        try {
            setCharsetBestEffort()
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readSetCallbackData(archive, null)
            val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
            Archive.readSetReadCallback<Any?>(archive) { _, _ ->
                buffer.clear()
                val bytesRead = try {
                    inputStream.read(buffer.array())
                } catch (e: IOException) {
                    throw e.toArchiveException("InputStream.read")
                }
                if (bytesRead != -1) {
                    buffer.limit(bytesRead)
                    buffer
                } else {
                    null
                }
            }
            Archive.readSetSkipCallback<Any?>(archive) { _, _, request ->
                try {
                    inputStream.skip(request)
                } catch (e: IOException) {
                    throw e.toArchiveException("InputStream.skip")
                }
            }
            for (password in passwords) {
                Archive.readAddPassphrase(archive, password.toByteArray())
            }
            Archive.readOpen1(archive)
            successful = true
        } finally {
            if (!successful) {
                close()
            }
        }
    }

    @Throws(ArchiveException::class)
    constructor(channel: SeekableByteChannel, passwords: List<String>) {
        var successful = false
        try {
            setCharsetBestEffort()
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readSetCallbackData(archive, null)
            val buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)
            Archive.readSetReadCallback<Any?>(archive) { _, _ ->
                buffer.clear()
                val bytesRead = try {
                    channel.read(buffer)
                } catch (e: IOException) {
                    throw e.toArchiveException("SeekableByteChannel.read")
                }
                if (bytesRead != -1) {
                    buffer.flip()
                    buffer
                } else {
                    null
                }
            }
            Archive.readSetSkipCallback<Any?>(archive) { _, _, request ->
                try {
                    channel.position(channel.position() + request)
                } catch (e: IOException) {
                    throw e.toArchiveException("SeekableByteChannel.position")
                }
                request
            }
            Archive.readSetSeekCallback<Any?>(archive) { _, _, offset, whence ->
                val newPosition: Long
                try {
                    newPosition = when (whence) {
                        OsConstants.SEEK_SET -> offset
                        OsConstants.SEEK_CUR -> channel.position() + offset
                        OsConstants.SEEK_END -> channel.size() + offset
                        else -> throw ArchiveException(
                            Archive.ERRNO_FATAL,
                            "Unknown whence $whence"
                        )
                    }
                    channel.position(newPosition)
                } catch (e: IOException) {
                    throw e.toArchiveException("SeekableByteChannel.position")
                }
                newPosition
            }
            for (password in passwords) {
                Archive.readAddPassphrase(archive, password.toByteArray())
            }
            Archive.readOpen1(archive)
            successful = true
        } finally {
            if (!successful) {
                close()
            }
        }
    }

    private fun IOException.toArchiveException(message: String): ArchiveException =
        when (this) {
            is InterruptedIOException -> ArchiveException(OsConstants.EINTR, message, this)
            else -> ArchiveException(Archive.ERRNO_FATAL, message, this)
        }

    @Throws(ArchiveException::class)
    fun readEntry(charset: Charset): Entry? {
        val entry = Archive.readNextHeader(archive)
        if (entry == 0L) {
            return null
        }
        val name =
            getEntryString(ArchiveEntry.pathnameUtf8(entry), ArchiveEntry.pathname(entry), charset)
                ?: throw ArchiveException(
                    Archive.ERRNO_FATAL, "pathname == null && pathnameUtf8 == null"
                )
        val isEncrypted = ArchiveEntry.isEncrypted(entry)
        val stat = ArchiveEntry.stat(entry)
        val lastModifiedTime = if (ArchiveEntry.mtimeIsSet(entry)) {
            FileTime.from(
                Instant.ofEpochSecond(stat.stMtim.tvSec, stat.stMtim.tvNsec)
            )
        } else {
            null
        }
        val lastAccessTime = if (ArchiveEntry.atimeIsSet(entry)) {
            FileTime.from(
                Instant.ofEpochSecond(stat.stAtim.tvSec, stat.stAtim.tvNsec)
            )
        } else {
            null
        }
        val creationTime = if (ArchiveEntry.birthtimeIsSet(entry)) {
            FileTime.from(
                Instant.ofEpochSecond(
                    ArchiveEntry.birthtime(entry), ArchiveEntry.birthtimeNsec(entry)
                )
            )
        } else {
            null
        }
        val type = PosixFileType.fromMode(ArchiveEntry.filetype(entry))
        val size = ArchiveEntry.size(entry)
        val compressedSize = compressedSizeFromBackend(
            stat = stat,
            entryType = type,
            entrySize = size,
            archiveFormat = archiveFormat(),
            primaryFilterCode = primaryFilterCode()
        )
        // TODO: There's no way to know if UID/GID is unset or root.
        val owner = PosixUser(
            stat.stUid, getEntryString(
                ArchiveEntry.unameUtf8(entry), ArchiveEntry.uname(entry), charset
            )?.toByteString()
        )
        val group = PosixGroup(
            stat.stGid, getEntryString(
                ArchiveEntry.gnameUtf8(entry), ArchiveEntry.gname(entry), charset
            )?.toByteString()
        )
        val mode = PosixFileMode.fromInt(ArchiveEntry.mode(entry))
        val symbolicLinkTarget =
            getEntryString(ArchiveEntry.symlinkUtf8(entry), ArchiveEntry.symlink(entry), charset)
        return Entry(
            name, isEncrypted, lastModifiedTime, lastAccessTime, creationTime, type, size, owner,
            group, mode, symbolicLinkTarget, compressedSize
        )
    }

    private fun getEntryString(stringUtf8: String?, string: ByteArray?, charset: Charset): String? =
        stringUtf8 ?: string?.toString(charset)

    @Throws(ArchiveException::class)
    fun newDataInputStream(): InputStream = DataInputStream()

    @Throws(ArchiveException::class)
    override fun close() {
        Archive.readFree(archive)
    }

    class Entry(
        val name: String,
        val isEncrypted: Boolean,
        val lastModifiedTime: FileTime?,
        val lastAccessTime: FileTime?,
        val creationTime: FileTime?,
        val type: PosixFileType,
        val size: Long,
        val owner: PosixUser?,
        val group: PosixGroup?,
        val mode: Set<PosixFileModeBit>,
        val symbolicLinkTarget: String?,
        val compressedSize: Long?,
        val backend: ArchiveReadBackend = ArchiveReadBackend.LIBARCHIVE,
        val backendIndex: Int? = null
    ) {
        val isDirectory: Boolean
            get() = type == PosixFileType.DIRECTORY

        val isSymbolicLink: Boolean
            get() = type == PosixFileType.SYMBOLIC_LINK
    }

    companion object {
        internal fun compressedSizeFromBackend(
            stat: ArchiveEntry.StructStat?,
            entryType: PosixFileType,
            entrySize: Long,
            archiveFormat: Int?,
            primaryFilterCode: Int?
        ): Long? {
            // First preference: libarchive stat blocks, which are backend/format dependent and may
            // be unavailable for some inputs.
            compressedSizeFromStat(stat)?.let { return it }

            // Fallback: for unfiltered container formats that store entries without per-entry
            // compression, compressed size equals payload size.
            if (entrySize < 0) {
                return null
            }
            if (entryType == PosixFileType.DIRECTORY) {
                return 0L
            }
            if (primaryFilterCode != null && primaryFilterCode != Archive.FILTER_NONE) {
                return null
            }
            val format = archiveFormat ?: return null
            val formatBase = format and Archive.FORMAT_BASE_MASK
            return when (formatBase) {
                Archive.FORMAT_TAR,
                Archive.FORMAT_CPIO,
                Archive.FORMAT_AR,
                Archive.FORMAT_ISO9660,
                Archive.FORMAT_MTREE,
                Archive.FORMAT_WARC,
                Archive.FORMAT_SHAR -> entrySize
                else -> null
            }
        }

        internal fun compressedSizeFromStat(stat: ArchiveEntry.StructStat?): Long? {
            val blocks = stat?.stBlocks ?: return null
            if (blocks <= 0) {
                return null
            }
            return if (blocks > Long.MAX_VALUE / 512L) {
                null
            } else {
                blocks * 512L
            }
        }
    }

    private fun archiveFormat(): Int? = runCatching {
        Archive.format(archive)
    }.getOrNull()?.takeIf { it != 0 }

    private fun primaryFilterCode(): Int? = runCatching {
        Archive.filterCode(archive, 0)
    }.getOrNull()?.takeIf { it != 0 }

    private inner class DataInputStream : InputStream() {
        private val oneByteBuffer = ByteBuffer.allocateDirect(1)

        @Throws(IOException::class)
        override fun read(): Int {
            read(oneByteBuffer)
            return if (oneByteBuffer.hasRemaining()) oneByteBuffer.get().toUByte().toInt() else -1
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val buffer = ByteBuffer.wrap(b, off, len)
            read(buffer)
            return if (buffer.hasRemaining()) buffer.remaining() else -1
        }

        @Throws(IOException::class)
        private fun read(buffer: ByteBuffer) {
            buffer.clear()
            Archive.readData(archive, buffer)
            buffer.flip()
        }
    }
}
