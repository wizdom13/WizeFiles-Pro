package com.wisso.wizefiles.util

@JvmInline
value class PathName(val value: String) {
    val fileName: String?
        get() = value.pathLeaf()

    val directoryName: String?
        get() = value.pathParent()

    companion object {
        const val SEPARATOR = '/'
    }
}

fun String.asPathName(): PathName {
    require(canRepresentPath())
    return PathName(this)
}

fun String.asPathNameOrNull(): PathName? =
    takeIf { it.canRepresentPath() }?.let(::PathName)

@JvmInline
value class FileName(val value: String) {
    val singleExtension: String
        get() = value.lastSuffix()

    val extensions: String
        get() {
            val last = singleExtension
            if (last.isEmpty() || last.lowercase() !in COMPOUND_ENDINGS) {
                return last
            }
            val prefixEnd = value.length - last.length - 1
            if (prefixEnd <= 0) {
                return last
            }
            val previous = value.substring(0, prefixEnd).lastSuffix()
            return if (previous.isEmpty()) last else previous + EXTENSION_SEPARATOR + last
        }

    val baseName: String
        get() {
            val suffix = extensions
            return if (suffix.isEmpty()) value else value.substring(0, value.length - suffix.length - 1)
        }

    companion object {
        const val EXTENSION_SEPARATOR = '.'

        private val COMPOUND_ENDINGS = setOf("bz", "bz2", "gz", "sit", "xz", "z")
    }
}

fun String.asFileName(): FileName {
    require(canRepresentFileName())
    return FileName(this)
}

fun String.asFileNameOrNull(): FileName? =
    takeIf { it.canRepresentFileName() }?.let(::FileName)

private fun String.pathLeaf(): String? {
    val separator = lastIndexOf(PathName.SEPARATOR)
    val start = separator + 1
    return if (start >= length) null else substring(start)
}

private fun String.pathParent(): String? {
    val separator = lastIndexOf(PathName.SEPARATOR)
    if (separator < 0) {
        return null
    }
    var end = separator
    while (end > 0 && this[end - 1] == PathName.SEPARATOR) {
        --end
    }
    return substring(0, end)
}

private fun String.lastSuffix(): String {
    val separator = lastIndexOf(FileName.EXTENSION_SEPARATOR)
    return if (separator < 0) "" else substring(separator + 1)
}

private fun String.canRepresentPath(): Boolean = isNotEmpty() && none { it == '\u0000' }

private fun String.canRepresentFileName(): Boolean =
    canRepresentPath() && none { it == PathName.SEPARATOR }
