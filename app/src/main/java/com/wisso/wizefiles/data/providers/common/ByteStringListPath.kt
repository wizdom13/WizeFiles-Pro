// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.core.android.compat.readBooleanCompat
import com.wisso.wizefiles.core.android.compat.writeBooleanCompat
import com.wisso.wizefiles.core.android.compat.writeParcelableListCompat
import com.wisso.wizefiles.util.readParcelableListCompat
import java.net.URI
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.ProviderMismatchException

abstract class ByteStringListPath<T : ByteStringListPath<T>> : AbstractPath<T>, Parcelable {
    protected val separator: Byte
    private val absolute: Boolean
    private val pathSegments: List<ByteString>

    @Volatile
    private var encodedCache: ByteString? = null

    constructor(separator: Byte, path: ByteString) {
        require(separator != NUL) { "Separator cannot be the nul character" }
        if (path.contains(NUL)) {
            throw InvalidPathException(path.toString(), "Path cannot contain nul characters")
        }
        this.separator = separator
        absolute = isPathAbsolute(path)
        pathSegments = parseSegments(path, separator)
        validateShape()
    }

    protected constructor(
        separator: Byte,
        isAbsolute: Boolean,
        segments: List<ByteString>
    ) {
        require(separator != NUL) { "Separator cannot be the nul character" }
        this.separator = separator
        absolute = isAbsolute
        pathSegments = segments.toList()
        validateShape()
    }

    override fun isAbsolute(): Boolean = absolute

    val fileNameByteString: ByteString?
        get() = pathSegments.lastOrNull()

    override fun getParent(): T? {
        if (pathSegments.isEmpty() || isEmpty || (!absolute && pathSegments.size == 1)) {
            return null
        }
        return createPath(absolute, pathSegments.subList(0, pathSegments.lastIndex))
    }

    override fun getNameCount(): Int = pathSegments.size

    override fun getName(index: Int): T =
        createPath(absolute = false, segments = listOf(getNameByteString(index)))

    fun getNameByteString(index: Int): ByteString = pathSegments[index]

    override fun subpath(beginIndex: Int, endIndex: Int): T =
        createPath(
            absolute = false,
            segments = pathSegments.subList(beginIndex, endIndex).toList()
        )

    override fun startsWith(other: Path): Boolean {
        if (this === other) {
            return true
        }
        val candidate = comparablePathOrNull(other) ?: return false
        return pathSegments.hasPrefix(candidate.pathSegments)
    }

    fun startsWith(other: ByteString): Boolean = startsWith(createPath(other))

    override fun endsWith(other: Path): Boolean {
        if (this === other) {
            return true
        }
        val candidate = comparablePathOrNull(other) ?: return false
        return pathSegments.hasSuffix(candidate.pathSegments)
    }

    fun endsWith(other: ByteString): Boolean = endsWith(createPath(other))

    override fun normalize(): T {
        val normalized = ArrayList<ByteString>(pathSegments.size)
        for (segment in pathSegments) {
            when {
                segment == DOT -> Unit
                segment != DOT_DOT -> normalized += segment
                normalized.isEmpty() -> if (!absolute) normalized += segment
                normalized.last() == DOT_DOT -> normalized += segment
                else -> normalized.removeAt(normalized.lastIndex)
            }
        }
        return if (!absolute && normalized.isEmpty()) {
            emptyPath()
        } else {
            createPath(absolute, normalized)
        }
    }

    override fun resolve(other: Path): T {
        val candidate = requireCompatible(other)
        if (candidate.isAbsolute) {
            return candidate
        }
        if (candidate.isEmpty) {
            @Suppress("UNCHECKED_CAST")
            return this as T
        }
        if (isEmpty) {
            return candidate
        }
        return createPath(absolute, pathSegments + candidate.pathSegments)
    }

    fun resolve(other: ByteString): T = resolve(createPath(other))

    fun resolveSibling(other: ByteString): T = resolveSibling(createPath(other))

    override fun relativize(other: Path): T {
        val candidate = requireCompatible(other)
        require(absolute == candidate.absolute) {
            "The other path must be as absolute as this path"
        }
        if (isEmpty) {
            return candidate
        }
        if (this == candidate) {
            return emptyPath()
        }

        val shared = sharedPrefixLength(pathSegments, candidate.pathSegments)
        val result = ArrayList<ByteString>(
            pathSegments.size - shared + candidate.pathSegments.size - shared
        )
        repeat(pathSegments.size - shared) {
            result += DOT_DOT
        }
        result += candidate.pathSegments.subList(shared, candidate.pathSegments.size)
        return createPath(absolute = false, segments = result)
    }

    override fun toUri(): URI =
        URI::class.create(uriScheme, uriAuthority, uriPath, uriQuery)

    override fun toAbsolutePath(): T {
        if (absolute) {
            @Suppress("UNCHECKED_CAST")
            return this as T
        }
        return defaultDirectory.resolve(this)
    }

    open fun toByteString(): ByteString {
        encodedCache?.let { return it }
        val rendered = ByteStringBuilder().apply {
            if (absolute && root != null) {
                append(separator)
            }
            pathSegments.forEachIndexed { index, segment ->
                if (index > 0) {
                    append(separator)
                }
                append(segment)
            }
        }.toByteString()
        encodedCache = rendered
        return rendered
    }

    override fun toString(): String = toByteString().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        other as ByteStringListPath<*>
        return separator == other.separator &&
            absolute == other.absolute &&
            pathSegments == other.pathSegments &&
            fileSystem == other.fileSystem
    }

    override fun hashCode(): Int {
        var result = separator.hashCode()
        result = 31 * result + absolute.hashCode()
        result = 31 * result + pathSegments.hashCode()
        result = 31 * result + fileSystem.hashCode()
        return result
    }

    override fun compareTo(other: Path): Int {
        if (javaClass != other.javaClass) {
            throw ClassCastException(other.toString())
        }
        @Suppress("UNCHECKED_CAST")
        val candidate = other as T
        if (provider != candidate.provider) {
            throw ClassCastException(other.toString())
        }
        return toByteString().compareTo(candidate.toByteString())
    }

    val nameByteStrings: Iterable<ByteString>
        get() = pathSegments

    val isEmpty: Boolean
        get() = !absolute &&
            pathSegments.size == 1 &&
            pathSegments.first() == ByteString.EMPTY

    protected abstract fun isPathAbsolute(path: ByteString): Boolean

    protected abstract fun createPath(path: ByteString): T

    protected abstract fun createPath(absolute: Boolean, segments: List<ByteString>): T

    protected open val uriScheme: String
        get() = fileSystem.provider().scheme

    protected open val uriAuthority: UriAuthority
        get() = UriAuthority.EMPTY

    protected open val uriPath: ByteString
        get() = toAbsolutePath().toByteString()

    protected open val uriQuery: ByteString?
        get() = null

    protected abstract val defaultDirectory: T

    protected constructor(source: Parcel) {
        separator = source.readByte()
        absolute = source.readBooleanCompat()
        pathSegments = source.readParcelableListCompat()
        validateShape()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte(separator)
        dest.writeBooleanCompat(absolute)
        dest.writeParcelableListCompat(pathSegments, flags)
    }

    private fun validateShape() {
        check(absolute || pathSegments.isNotEmpty()) {
            "Non-absolute path must not be empty"
        }
    }

    private fun comparablePathOrNull(other: Path): T? {
        if (javaClass != other.javaClass || provider != other.provider) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val candidate = other as T
        return candidate.takeIf { fileSystem == it.fileSystem }
    }

    private fun requireCompatible(other: Path): T {
        if (javaClass != other.javaClass || provider != other.provider) {
            throw ProviderMismatchException(other.toString())
        }
        @Suppress("UNCHECKED_CAST")
        val candidate = other as T
        require(fileSystem == candidate.fileSystem) {
            "The other path must have the same file system as this path"
        }
        return candidate
    }

    private fun emptyPath(): T =
        createPath(absolute = false, segments = listOf(ByteString.EMPTY))

    private companion object {
        const val NUL: Byte = 0
        val DOT = ".".toByteString()
        val DOT_DOT = "..".toByteString()

        fun parseSegments(path: ByteString, separator: Byte): List<ByteString> {
            if (path.isEmpty()) {
                return listOf(ByteString.EMPTY)
            }
            val result = ArrayList<ByteString>()
            var cursor = 0
            while (cursor < path.length) {
                while (cursor < path.length && path[cursor] == separator) {
                    ++cursor
                }
                if (cursor >= path.length) {
                    break
                }
                var end = cursor
                while (end < path.length && path[end] != separator) {
                    ++end
                }
                result += path.substring(cursor, end)
                cursor = end
            }
            return result
        }

        fun List<ByteString>.hasPrefix(prefix: List<ByteString>): Boolean {
            if (prefix.size > size) {
                return false
            }
            return prefix.indices.all { this[it] == prefix[it] }
        }

        fun List<ByteString>.hasSuffix(suffix: List<ByteString>): Boolean {
            if (suffix.size > size) {
                return false
            }
            val offset = size - suffix.size
            return suffix.indices.all { this[offset + it] == suffix[it] }
        }

        fun sharedPrefixLength(
            first: List<ByteString>,
            second: List<ByteString>
        ): Int {
            val limit = minOf(first.size, second.size)
            var shared = 0
            while (shared < limit && first[shared] == second[shared]) {
                ++shared
            }
            return shared
        }
    }
}
