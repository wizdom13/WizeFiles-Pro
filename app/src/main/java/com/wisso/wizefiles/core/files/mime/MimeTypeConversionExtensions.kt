package com.wisso.wizefiles.core.files.mime

import android.webkit.MimeTypeMap
import com.wisso.wizefiles.provider.common.PosixFileType
import com.wisso.wizefiles.util.asFileName
import com.wisso.wizefiles.util.asPathName

fun MimeType.Companion.guessFromPath(path: String): MimeType {
    val name = path.asPathName().fileName ?: return DIRECTORY
    return guessFromExtension(name.asFileName().singleExtension)
}

fun MimeType.Companion.guessFromPaths(paths: Iterable<String?>): MimeType {
    var firstGuess: MimeType? = null
    for (path in paths) {
        if (path.isNullOrBlank()) {
            continue
        }
        val guess = guessFromPath(path)
        if (firstGuess == null) {
            firstGuess = guess
        }
        if (guess != GENERIC) {
            return guess
        }
    }
    return firstGuess ?: GENERIC
}

fun MimeType.Companion.guessFromPaths(vararg paths: String?): MimeType =
    guessFromPaths(paths.asIterable())

fun MimeType.Companion.guessFromExtension(extension: String): MimeType =
    ExtensionMimeCatalog.resolve(extension) { normalizedExtension ->
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(normalizedExtension)
    }

fun MimeType.Companion.forSpecialPosixFileType(type: PosixFileType): MimeType? {
    val value = when (type) {
        PosixFileType.CHARACTER_DEVICE -> "inode/chardevice"
        PosixFileType.BLOCK_DEVICE -> "inode/blockdevice"
        PosixFileType.FIFO -> "inode/fifo"
        PosixFileType.SYMBOLIC_LINK -> "inode/symlink"
        PosixFileType.SOCKET -> "inode/socket"
        else -> return null
    }
    return value.asMimeType()
}

val MimeType.extension: String?
    get() = MimeTypeMap.getSingleton().getExtensionFromMimeType(value)

val MimeType.intentType: String
    get() = asIntentMimeType().value

private fun MimeType.asIntentMimeType(): MimeType {
    val valueForIntent = when (value) {
        "application/ecmascript" -> "text/ecmascript"
        "application/javascript" -> "text/javascript"
        "application/json" -> "text/json"
        "application/typescript" -> "text/typescript"
        "application/yaml" -> "text/x-yaml"
        "application/x-sh", "application/x-shellscript" -> "text/x-shellscript"
        MimeType.GENERIC.value -> MimeType.ANY.value
        else -> return this
    }
    return valueForIntent.asMimeType()
}

val Collection<MimeType>.intentType: String
    get() {
        val iterator = iterator()
        if (!iterator.hasNext()) {
            return MimeType.ANY.value
        }

        val first = iterator.next().asIntentMimeType()
        var allMatchFirst = true
        var commonTopLevelType = true
        while (iterator.hasNext()) {
            val current = iterator.next().asIntentMimeType()
            allMatchFirst = allMatchFirst && first.match(current)
            commonTopLevelType = commonTopLevelType && first.type == current.type
        }
        return when {
            allMatchFirst -> first.value
            commonTopLevelType -> MimeType.of(first.type, "*", null).value
            else -> MimeType.ANY.value
        }
    }
